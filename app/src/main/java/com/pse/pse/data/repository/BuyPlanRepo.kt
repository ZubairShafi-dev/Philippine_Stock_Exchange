package com.pse.pse.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class BuyPlanRepo(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    enum class Status { SUCCESS, MIN_INVEST_ERROR, INSUFFICIENT_BALANCE, FAILURE }

    private object Paths {
        val INVEST_CUR_BAL = FieldPath.of("investment", "currentBalance")
        val REFERRAL_PROFIT = FieldPath.of("earnings", "referralProfit")
    }

    /**
     * @param uid   – the custom userId field ("userId") stored in accounts and users docs (e.g. "C6756")
     * @param pkgId – the Firestore document ID of the plan to buy
     */
    suspend fun buyPlan(
        uid: String,
        pkgId: String,
        amount: Double
    ): Status = withContext(Dispatchers.IO) {
        Log.d("BuyPlanRepo", "buyPlan called with uid='$uid', pkgId='$pkgId', amount=$amount")

        if (uid.isBlank() || pkgId.isBlank()) {
            Log.e("BuyPlanRepo", "FAILURE: Blank uid or pkgId")
            return@withContext Status.FAILURE
        }

        try {
            // 0️⃣ Lookup account document by custom userId field
            val accountQuery = db.collection("accounts")
                .whereEqualTo("userId", uid)
                .limit(1)
                .get().await()
            if (accountQuery.isEmpty) {
                Log.e("BuyPlanRepo", "FAILURE: No account found for userId='$uid'")
                return@withContext Status.FAILURE
            }
            val accountDoc = accountQuery.documents.first()
            val accountId = accountDoc.id
            Log.d("BuyPlanRepo", "Found accountId='$accountId' for userId='$uid'")

            // 1️⃣ Load user document to get referralCode
            val userQuery = db.collection("users")
                .whereEqualTo("uid", uid)
                .limit(1)
                .get().await()
            if (userQuery.isEmpty) {
                Log.e("BuyPlanRepo", "FAILURE: No user document for uid='$uid'")
                return@withContext Status.FAILURE
            }
            val userSnap = userQuery.documents.first()
            val refCode = userSnap.getString("referralCode")?.takeIf { it.isNotBlank() }
            Log.d("BuyPlanRepo", "referralCode='$refCode'")

            // 2️⃣ Check referrer's account & active plan
            var refAccountId: String? = null
            var isRefActive = false
            if (refCode != null) {
                val refAcctQ = db.collection("accounts")
                    .whereEqualTo("userId", refCode)
                    .limit(1)
                    .get().await()
                if (!refAcctQ.isEmpty) {
                    refAccountId = refAcctQ.documents.first().id
                    Log.d("BuyPlanRepo", "referrer accountId='$refAccountId'")
                    val planQ = db.collection("userPlans")
                        .document(refAccountId)
                        .collection("plans")
                        .whereEqualTo("status", "active")
                        .limit(1)
                        .get().await()
                    isRefActive = !planQ.isEmpty
                    Log.d(
                        "BuyPlanRepo",
                        "isRefActive=$isRefActive for referrerAcct='$refAccountId'"
                    )
                }
            }

            // 3️⃣ Transaction: debit purchaser, credit referral, create plan & audit
            return@withContext db.runTransaction { tr ->
                // a) Load plan metadata
                val pkgRef = db.collection("plans").document(pkgId)
                val pkgSnap = tr.get(pkgRef)
                if (!pkgSnap.exists()) {
                    Log.e("BuyPlanRepo", "FAILURE: Plan not found pkgId='$pkgId'")
                    return@runTransaction Status.FAILURE
                }
                val minInv = pkgSnap.getDouble("minAmount") ?: 0.0
                val maxInv = pkgSnap.getDouble("maxAmount")
                val roiPct = pkgSnap.getDouble("dailyPercentage") ?: 0.0
                val dirPct = pkgSnap.getDouble("directProfit") ?: 0.0
                val payoutPct =
                    pkgSnap.getDouble("totalPayout") ?: return@runTransaction Status.FAILURE

                // b) Minimum investment check
                if (amount < minInv) return@runTransaction Status.MIN_INVEST_ERROR

                // c) Check purchaser balance
                val buyerAccRef = db.collection("accounts").document(accountId)
                val buyerSnap = tr.get(buyerAccRef)
                val curBal = (buyerSnap.get("investment") as? Map<*, *>)
                    ?.get("currentBalance") as? Number? ?: 0.0
                Log.d("BuyPlanRepo", "CurrentBalance=$curBal, required=$amount")


                // d) Credit referral bonus
                if (isRefActive && refAccountId != null) {
                    val bonus = amount * dirPct / 100
                    tr.update(
                        db.collection("accounts").document(refAccountId),
                        Paths.REFERRAL_PROFIT,
                        FieldValue.increment(bonus)
                    )
                    Log.d(
                        "BuyPlanRepo",
                        "Credited referral bonus=$bonus to referrerAcct='$refAccountId'"
                    )
                }

                // e) Debit purchaser balance
                tr.update(
                    buyerAccRef,
                    Paths.INVEST_CUR_BAL,
                    FieldValue.increment(-amount)
                )
                Log.d("BuyPlanRepo", "Debited amount=$amount from accountId='$accountId'")

                // f) Create userPlan entry
                val upRef = db.collection("userPlans")
                    .document(accountId)
                    .collection("boughtPlans")
                    .document()
                tr.set(
                    upRef,
                    mapOf(
                        "pkgId" to pkgId,
                        "principal" to amount,
                        "roiPercent" to roiPct,
                        "roiAmount" to ((amount * roiPct) / 100),
                        "totalPayoutAmount" to ((amount * payoutPct) / 100),
                        "uplineReferralBonus" to ((amount * dirPct) / 100),
                        "status" to "active",
                        "buyDate" to FieldValue.serverTimestamp(),
                        "totalAccumulated" to 0.0,
                        "lastCollectedDate" to FieldValue.serverTimestamp()
                    )
                )
                Log.d("BuyPlanRepo", "Created userPlan for accountId='$accountId'")

                // g) Audit log
                tr.set(
                    db.collection("transactions").document(),
                    mapOf(
                        "uid" to uid,
                        "type" to "Plan Purchase",
                        "amount" to amount,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "planId" to pkgId,
                        "referrerId" to (refAccountId ?: "")
                    )
                )
                Log.d("BuyPlanRepo", "Audit log entry created")

                Status.SUCCESS
            }.await()
        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "TX failed", e)
            return@withContext Status.FAILURE
        }
    }
}
