package com.pse.pse.data.repository

import android.app.Application
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pse.pse.fcm.AccessToken
import com.pse.pse.fcm.Fcm
import com.pse.pse.models.TransactionModel
import com.pse.pse.utils.SharedPrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository for handling deposit and withdrawal transactions.
 * Encapsulates Firestore operations and business logic (validation).
 */
class TransactionRepository(
    application: Application,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val prefs: SharedPrefManager = SharedPrefManager(application.applicationContext)
) {

    private var approvalListener: ListenerRegistration? = null
    private val _liveBalance = MutableStateFlow<Double?>(null)
    val liveBalance: StateFlow<Double?> = _liveBalance.asStateFlow()


    companion object {
        private const val TAG = "TransactionRepo"
        private const val COLL_TRANSACTIONS = "transactions"
        private const val COLL_ACCOUNTS = "accounts"
    }

    /**
     * Handles withdrawal: validates wallet address and balance,
     * then creates a pending transaction (without changing balance).
     */
    suspend fun submitWithdrawal(amount: Double, address: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val userId = prefs.getId() ?: return@withContext false

                // Get document references outside the transaction
                val userQuery =
                    db.collection("users").whereEqualTo("uid", userId).limit(1).get().await()
                val userDoc = userQuery.documents.firstOrNull() ?: return@withContext false

                val accQuery =
                    db.collection(COLL_ACCOUNTS).whereEqualTo("userId", userId).limit(1).get()
                        .await()
                val accDoc = accQuery.documents.firstOrNull() ?: return@withContext false

                // ✅ Use await() to extract result of transaction
                val result = db.runTransaction { tr ->
                    val userSnap = tr.get(userDoc.reference)
                    val isBlocked = (userSnap.get("isBlocked") as? Boolean) ?: false
                    if (isBlocked) throw Exception("User is blocked")

                    if (address.isBlank()) throw Exception("Invalid wallet address")

                    val accSnap = tr.get(accDoc.reference)
                    val accRef = accDoc.reference

                    val investment = accSnap.get("investment") as? Map<*, *> ?: emptyMap<Any, Any>()
                    val curBal = (investment["currentBalance"] as? Number)?.toDouble() ?: 0.0
                    val remBal = (investment["remainingBalance"] as? Number)?.toDouble() ?: 0.0

                    if (curBal < amount || remBal < amount) throw Exception("Insufficient funds")

                    val txRef = db.collection(COLL_TRANSACTIONS).document()
                    val tx = TransactionModel(
                        transactionId = txRef.id,
                        userId = userId,
                        amount = amount,
                        type = TransactionModel.TYPE_WITHDRAW,
                        address = address,
                        status = TransactionModel.STATUS_PENDING,
                        balanceUpdated = false,
                        timestamp = Timestamp.now()
                    )
                    tr.set(txRef, tx.toMap())

                    tr.update(
                        accRef, mapOf(
                            "investment.currentBalance" to curBal - amount,
                            "investment.remainingBalance" to remBal - amount
                        )
                    )
                    true
                }.await() // ✅ This resolves the task into Boolean

                if (result) sendWithdrawNotificationToAdmin(userId, amount)

                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "submitWithdrawal failed", e)
                return@withContext false
            }
        }


    private fun sendWithdrawNotificationToAdmin(userId: String, amount: Double) {
        val db = FirebaseFirestore.getInstance()

        // Fetch admin document (assuming only one admin)
        db.collection("Admin").limit(1).get().addOnSuccessListener { snapshot ->
            val adminDoc = snapshot.documents.firstOrNull()
            val adminToken = adminDoc?.getString("deviceToken")

            if (adminToken.isNullOrEmpty()) {
                Log.e("FCM", "Admin device token not found")
                return@addOnSuccessListener
            }

            val title = "New Withdrawal Request"
            val body = "User $userId requested $$amount withdrawal."

            AccessToken.getAccessTokenAsync(object : AccessToken.AccessTokenCallback {
                override fun onAccessTokenReceived(token: String?) {
                    if (!token.isNullOrEmpty()) {
                        Fcm().sendFCMNotification(adminToken, title, body, token)
                    } else {
                        Log.e("FCM", "Access token was null or empty")
                    }
                }
            })
        }.addOnFailureListener {
            Log.e("FCM", "Failed to fetch admin token", it)
        }
    }


    suspend fun deposit(amount: Double, address: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = prefs.getId() ?: return@withContext false
                if (address.isBlank()) {
                    Log.w(TAG, "Invalid wallet address")
                    return@withContext false
                }

                val txRef = db.collection(COLL_TRANSACTIONS).document()
                val tx = TransactionModel(
                    transactionId = txRef.id,
                    userId = userId,
                    amount = amount,
                    type = TransactionModel.TYPE_DEPOSIT,
                    address = address,
                    status = TransactionModel.STATUS_PENDING,
                    balanceUpdated = false,
                    timestamp = Timestamp.now()
                )

                db.collection(COLL_TRANSACTIONS).document(txRef.id).set(tx).await()
                true // ✅ success
            } catch (e: Exception) {
                Log.e(TAG, "Deposit failed", e)
                false // ❌ failure
            }
        }
    }

    /**
     * Fetches all transactions for current user, sorted by timestamp locally.
     */
    suspend fun getHistory(): List<TransactionModel> = withContext(Dispatchers.IO) {
        try {
            val userId = prefs.getId()
            db.collection(COLL_TRANSACTIONS).whereEqualTo("userId", userId).get()
                .await().documents.mapNotNull { it.toObject(TransactionModel::class.java) }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch history failed", e)
            emptyList()
        }
    }

    /**
     * Fetches the current balance for the logged-in user
     */
    suspend fun getCurrentBalance(): Double? = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = prefs.getId() ?: return@withContext null
            val snap =
                db.collection(COLL_ACCOUNTS).whereEqualTo("userId", userId).limit(1).get().await()
            val doc = snap.documents.firstOrNull()
            val balance = doc?.get("investment.currentBalance") as? Number
            balance?.toDouble()
        } catch (e: Exception) {
            Log.e(TAG, "Get balance failed", e)
            null
        }
    }

    /** Begin listening for any approved (but not yet applied) transactions. */
    fun startBalanceSync() {
        val userId = prefs.getId() ?: return            // no user – no listener
        if (approvalListener != null) return            // already running

        approvalListener =
            db.collection(COLL_TRANSACTIONS).whereEqualTo(TransactionModel.FIELD_USER_ID, userId)
                .whereIn(
                    TransactionModel.FIELD_STATUS, listOf(
                        TransactionModel.STATUS_APPROVED,
                        TransactionModel.STATUS_REJECTED
                    )
                )
                .whereEqualTo(TransactionModel.FIELD_BALANCE_UPDATED, false)
                .addSnapshotListener { snap, err ->
                    if (err != null || snap == null || snap.isEmpty) return@addSnapshotListener

                    for (doc in snap.documents) {
                        applyApprovedTx(doc)
                    }
                }
    }

    /** Stop the listener (call in ViewModel.onCleared). */
    fun stopBalanceSync() = approvalListener?.remove().also { approvalListener = null }

    /**
     * Atomically move money **once** and mark the tx as handled so it
     * never triggers again on other devices or on app restart.
     */
    private fun applyApprovedTx(txSnap: DocumentSnapshot) {
        val userId = prefs.getId() ?: return
        val amount = txSnap.getDouble(TransactionModel.FIELD_AMOUNT) ?: return
        val type = txSnap.getString(TransactionModel.FIELD_TYPE) ?: return

        // ✅ External early exit (safety net – won't catch crash-in-middle cases)
        val alreadyUpdated = txSnap.getBoolean(TransactionModel.FIELD_BALANCE_UPDATED) ?: false
        if (alreadyUpdated) {
            Log.d(TAG, "⛔ Transaction ${txSnap.id} already marked balanceUpdated. Skipping.")
            return
        }

        // 🔍 Look up user's account
        db.collection(COLL_ACCOUNTS).whereEqualTo("userId", userId).limit(1).get()
            .addOnSuccessListener { qs ->
                val accRef = qs.documents.firstOrNull()?.reference ?: return@addOnSuccessListener

                // 🔐 Entire logic runs in Firestore transaction to prevent race/refund errors
                db.runTransaction { tr ->
                    val txDoc = tr.get(txSnap.reference)
                    val accSnap = tr.get(accRef)

                    // ✅ DOUBLE-CHECK INSIDE TX to prevent repeat refund
                    val balanceAlreadyUpdated =
                        txDoc.getBoolean(TransactionModel.FIELD_BALANCE_UPDATED) ?: false
                    if (balanceAlreadyUpdated) {
                        Log.d(
                            TAG,
                            "⛔ Transaction ${txSnap.id} already updated INSIDE transaction. Skipping."
                        )
                        return@runTransaction null
                    }

                    val curBal = accSnap.getDouble("investment.currentBalance") ?: 0.0
                    val remBal = accSnap.getDouble("investment.remainingBalance") ?: 0.0
                    val totalDep = accSnap.getDouble("investment.totalDeposit") ?: 0.0

                    var newCurBal = curBal
                    var newRemBal = remBal
                    var newTotalDep = totalDep
                    var newStatus = txDoc.getString(TransactionModel.FIELD_STATUS)
                        ?: TransactionModel.STATUS_PENDING

                    when (type) {
                        TransactionModel.TYPE_DEPOSIT -> {
                            newCurBal += amount
                            newRemBal += amount
                            newTotalDep += amount
                            newStatus = TransactionModel.STATUS_APPROVED

                            tr.update(
                                accRef, mapOf(
                                    "investment.currentBalance" to newCurBal,
                                    "investment.remainingBalance" to newRemBal,
                                    "investment.totalDeposit" to newTotalDep
                                )
                            )
                        }

                        TransactionModel.TYPE_WITHDRAW -> {
                            val curStatus = txDoc.getString(TransactionModel.FIELD_STATUS)
                            if (curStatus == TransactionModel.STATUS_REJECTED) {
                                // Refund
                                newCurBal += amount
                                newRemBal += amount

                                tr.update(
                                    accRef, mapOf(
                                        "investment.currentBalance" to newCurBal,
                                        "investment.remainingBalance" to newRemBal
                                    )
                                )

                                newStatus = TransactionModel.STATUS_REJECTED
                                Log.d(TAG, "✅ Refunded $amount for rejected tx ${txSnap.id}")
                            } else {
                                // No deduction here — already deducted during request
                                newStatus = TransactionModel.STATUS_APPROVED
                            }
                        }
                    }

                    // ✅ Mark transaction as updated (prevents re-trigger)
                    tr.update(
                        txSnap.reference, mapOf(
                            TransactionModel.FIELD_STATUS to newStatus,
                            TransactionModel.FIELD_BALANCE_UPDATED to true
                        )
                    )

                    newCurBal // return for .addOnSuccessListener
                }.addOnSuccessListener { updatedBal ->
                    updatedBal?.let {
                        _liveBalance.value = it
                        Log.d(TAG, "✅ Final balance after tx ${txSnap.id}: $it")

                        if (type == TransactionModel.TYPE_DEPOSIT) {

                        }
                    }
                }.addOnFailureListener { e ->
                    Log.e(TAG, "❌ applyApprovedTx failed for ${txSnap.id}", e)
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "❌ Account lookup failed for userId=$userId", e)
            }
    }


    /**
     * After a deposit by `userId` of `amount`, find that user's referrer
     * and credit them a 5% bonus.
     */
   /* private fun creditReferrerBonus(depositorId: String, amount: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1) Find depositor’s referrerCode
                val userSnap = db.collection("users")
                    .whereEqualTo("uid", depositorId)
                    .limit(1)
                    .get()
                    .await()
                val referrerUid = userSnap.documents
                    .firstOrNull()
                    ?.getString("referralCode")
                    .takeIf { !it.isNullOrBlank() }
                    ?: return@launch

                // 2) Calculate bonus
                val bonus = amount * 0.05

                // 3) Batch-update balances AND write a transaction entry
                val batch = db.batch()
                val accRef = db.collection(COLL_ACCOUNTS)
                    .whereEqualTo("userId", referrerUid)
                    .limit(1)
                    .get()
                    .await()
                    .documents
                    .firstOrNull()
                    ?.reference
                    ?: return@launch

                // a) balances
                batch.update(
                    accRef, mapOf(
                        "investment.currentBalance" to FieldValue.increment(bonus),
                        "investment.remainingBalance" to FieldValue.increment(bonus),
                        "earnings.referralProfit" to FieldValue.increment(bonus),
                        "earnings.totalEarned" to FieldValue.increment(bonus)
                    )
                )

                // b) transaction
                val txDoc = db.collection(COLL_TRANSACTIONS).document()
                val referralTxn = TransactionModel(
                    transactionId = txDoc.id,
                    userId = referrerUid,
                    amount = bonus,
                    type = TransactionModel.TYPE_REFERRAL,
                    address = "Referral $depositorId",
                    status = TransactionModel.STATUS_RECEIVED,
                    balanceUpdated = true,
                    timestamp = Timestamp.now()
                )
                batch.set(txDoc, referralTxn.toMap())

                // commit
                batch.commit().await()

                Log.d(TAG, "✅ Referrer $referrerUid credited & logged bonus: $bonus")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to credit referrer bonus", e)
            }
        }
    }*/
}