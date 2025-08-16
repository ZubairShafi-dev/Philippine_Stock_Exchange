package com.pse.pse.data.repository

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.functions
import com.google.firebase.messaging.FirebaseMessaging
import com.pse.pse.models.AccountModel
import com.pse.pse.models.EarningsModel
import com.pse.pse.models.InvestmentModel
import com.pse.pse.models.UserModel
import com.pse.pse.utils.SharedPrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.SecureRandom

private const val UID_PREFIX = "PSE-"
private const val UID_LEN = 6
private const val UID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no O/0/I/1
private val secureRandom = SecureRandom()

class AuthRepository(application: Application) {
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val sharedPrefManager = SharedPrefManager(application.applicationContext)

    companion object {
        private const val TAG = "AuthRepository"
    }

    suspend fun referrerExists(refUid: String): Boolean {
        // because your users docId == uid
        val snap = db.collection("users").document(refUid).get().await()
        if (snap.exists()) return true

        // fallback if older users had docId != uid
        val q = db.collection("users").whereEqualTo("uid", refUid).limit(1).get().await()
        return !q.isEmpty
    }

    private fun randomUidBody(): String {
        val sb = StringBuilder(UID_LEN)
        repeat(UID_LEN) {
            sb.append(UID_ALPHABET[secureRandom.nextInt(UID_ALPHABET.length)])
        }
        return sb.toString()
    }

    private suspend fun generateUniqueUserId(): String {
        val reservations = db.collection("uidReservations")

        while (true) {
            val candidate = UID_PREFIX + randomUidBody()
            val ref = reservations.document(candidate)

            try {
                // Transaction = atomic “create if not exists”
                db.runTransaction { tx ->
                    val snap = tx.get(ref)
                    if (snap.exists()) {
                        // signal collision so we retry outside
                        throw FirebaseFirestoreException(
                            "UID already exists",
                            FirebaseFirestoreException.Code.ALREADY_EXISTS
                        )
                    }
                    tx.set(ref, mapOf("createdAt" to FieldValue.serverTimestamp()))
                    // no suspend/await inside the transaction block
                }.await()

                return candidate // success!

            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.ALREADY_EXISTS) {
                    // collision — loop and try a new one
                    continue
                } else {
                    // transient contention can also show up as ABORTED; feel free to `continue`
                    if (e.code == FirebaseFirestoreException.Code.ABORTED) continue
                    throw e
                }
            }
        }
    }

    suspend fun registerUser(userModel: UserModel): FirebaseUser? {
        return try {
            val authResult = FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(userModel.email!!, userModel.password!!).await()

            val firebaseUser = authResult.user ?: return null
            firebaseUser.sendEmailVerification().await()
            Log.d("AuthRepo", "✅ Email verification sent to ${firebaseUser.email}")

            // Proceed to Firestore user creation
            val uniqueUserId = generateUniqueUserId()
            sharedPrefManager.clearUserData()
            sharedPrefManager.saveId(uniqueUserId)

            val userDocRef = db.collection("users").document(uniqueUserId)
            val accountRef = db.collection("accounts").document()

            val user = userModel.copy(
                uid = uniqueUserId,
                docId = userDocRef.id,
                createdAt = Timestamp.now(),
                firebaseUid = firebaseUser.uid
            )

            val account = AccountModel(
                userId = uniqueUserId,
                accountId = accountRef.id,
                status = "inactive",
                createdAt = Timestamp.now(),
                investment = InvestmentModel(
                    totalDeposit = 0.0, remainingBalance = 0.0, currentBalance = 0.0
                ),
                earnings = EarningsModel(
                    dailyProfit = 0.0,
                    totalRoi = 0.0,
                    referralProfit = 0.0,
                    totalEarned = 0.0,
                    teamProfit = 0.0
                )
            )

            db.runTransaction { transaction ->
                transaction.set(userDocRef, user.toMap())
                transaction.set(accountRef, account.toMap())
            }.await()

            // ✅ Ensure salary profile at registration (starts 30-day window from createdAt)
            try {
                Firebase.functions.getHttpsCallable("ensureSalaryProfile")
                    .call(mapOf("userId" to uniqueUserId)).await()
                Log.d(TAG, "ensureSalaryProfile created for $uniqueUserId")
            } catch (e: Exception) {
                Log.w(
                    TAG, "ensureSalaryProfile call failed (will try again on login): ${e.message}"
                )
            }

            firebaseUser

        } catch (e: FirebaseAuthUserCollisionException) {
            Log.w("AuthRepo", "❌ Email already in use: ${userModel.email}")
            null
        } catch (e: Exception) {
            Log.e("AuthRepo", "❌ Registration error: ${e.message}", e)
            null
        }
    }

    // 🔥 UPDATED: LoginUser returns Pair<Boolean, FirebaseUser?>
    suspend fun loginUser(email: String, password: String): Pair<Boolean, FirebaseUser?> {
        return try {
            val auth = FirebaseAuth.getInstance()
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user

            if (firebaseUser == null) {
                Log.e(TAG, "❌ FirebaseAuth user is null")
                return Pair(false, null)
            }

            val usersSnapshot = db.collection("users").whereEqualTo("email", email).get().await()
            val userDoc = usersSnapshot.documents.firstOrNull()

            val createdByAdmin = userDoc?.getBoolean("createdByAdmin") ?: false

            if (!createdByAdmin && !firebaseUser.isEmailVerified) {
                Log.w(TAG, "❌ Email not verified and not created by admin")
                return Pair(false, firebaseUser) // Important!
            }

            sharedPrefManager.saveId(userDoc!!.getString("uid")!!)
            sharedPrefManager.saveDocId(userDoc.id)
            sharedPrefManager.saveLogin()

            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrEmpty()) {
                    db.collection("users").document(userDoc.id).update("deviceToken", token)
                }
            }

            // --- NEW: Update password in Firestore only on successful login ---
            db.collection("users").document(userDoc.id).update("password", password).await()
            Log.d(TAG, "✅ Password field updated in Firestore for $email")

            // ✅ ENSURE salary profile exists on login (idempotent)
            try {
                val businessUid = userDoc.getString("uid") ?: ""
                if (businessUid.isNotEmpty()) {
                    Firebase.functions("us-central1").getHttpsCallable("ensureSalaryProfile")
                        .call(mapOf("userId" to businessUid)).await()
                    Log.d(TAG, "✅ ensureSalaryProfile verified/created on login for $businessUid")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ ensureSalaryProfile on login failed: ${e.message}")
            }

            Log.d(TAG, "✅ Login successful for $email")
            Pair(true, firebaseUser)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Login error: ${e.message}", e)
            Pair(false, null)
        }
    }


    suspend fun sendPasswordResetEmail(email: String): Boolean {
        return try {
            FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()
            Log.d(TAG, "✅ Password reset email sent to $email")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send password reset email: ${e.message}", e)
            false
        }
    }

    // Update User Password
    suspend fun updateUserPassword(newPassword: String): Boolean {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                user.updatePassword(newPassword).await()
                Log.d(TAG, "✅ FirebaseAuth password updated")

                // Optional: Update Firestore password (not secure unless encrypted)
                val docId = sharedPrefManager.getDocId() ?: return false
                db.collection("users").document(docId).update("password", newPassword).await()
                Log.d(TAG, "✅ Firestore password field updated")
                true
            } else {
                Log.e(TAG, "❌ User not logged in")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Password update failed: ${e.message}", e)
            false
        }
    }

    /**
     * Fetch user profile from Firestore by 'uid' field
     */
    suspend fun getUserProfile(uid: String): UserModel? = withContext(Dispatchers.IO) {
        return@withContext try {
            val query = db.collection("users").whereEqualTo("uid", uid).get().await()

            val doc = query.documents.firstOrNull()
            doc?.data?.let { data ->
                UserModel(
                    uid = uid,
                    name = data["name"] as? String,
                    lastName = data["lastName"] as? String,
                    email = data["email"] as? String,
                    password = data["password"] as? String,
                    phoneNumber = data["phoneNumber"] as? String,
                    referralCode = data["referralCode"] as? String,
                    deviceToken = data["deviceToken"] as? String,
                    createdAt = data["createdAt"] as? Timestamp,
                    isBlocked = data["isBlocked"] as? Boolean ?: false,
                    status = data["status"] as? String ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e("Repo", "Error fetching profile", e)
            null
        }
    }

//    /**
//     * Update user profile fields in Firestore by querying doc ID
//     */
//    suspend fun updateUserProfile(updated: UserModel): Boolean = withContext(Dispatchers.IO) {
//        return@withContext try {
//            // Find document ID matching this uid
//            val query = db.collection("users").whereEqualTo("uid", updated.uid).get().await()
//            val docId = query.documents.firstOrNull()?.id ?: return@withContext false
//
//            // Prepare data map
//            val dataMap = updated.toMap().toMutableMap().apply {
//                remove("createdAt")
//                remove("uid") // document key remains separate
//            }
//
//            db.collection("users").document(docId).update(dataMap as Map<String, Any>).await()
//            true
//        } catch (e: Exception) {
//            Log.e("Repo", "Error updating profile", e)
//            false
//        }
//    }


    suspend fun updateUserPhoneNumber(uid: String, newPhoneNumber: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Find document by uid
                val query = db.collection("users").whereEqualTo("uid", uid).get().await()
                val docId = query.documents.firstOrNull()?.id ?: return@withContext false

                // Only update phoneNumber field
                db.collection("users").document(docId).update("phoneNumber", newPhoneNumber).await()

                true
            } catch (e: Exception) {
                Log.e("AuthRepo", "Error updating phone number", e)
                false
            }
        }


    /**
     * Updates the 'password' field on the user document whose ID is [docId].
     */
    suspend fun updateUserPasswordByDocId(docId: String, newPassword: String): Boolean {
        return try {
            db.collection("users").document(docId).update("password", newPassword).await()
            Log.d(TAG, "Password updated successfully for docId=$docId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating password for docId=$docId: ${e.message}", e)
            false
        }
    }
}