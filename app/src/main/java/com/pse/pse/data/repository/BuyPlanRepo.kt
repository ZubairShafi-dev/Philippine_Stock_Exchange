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
        val INVEST_REMAINING_BAL = FieldPath.of("investment", "remainingBalance") // optional
        val REFERRAL_PROFIT = FieldPath.of("earnings", "referralProfit")
    }

    /**
     * @param uid   – the custom userId field ("userId") stored in accounts and users docs (e.g. "C6756")
     * @param pkgId – the Firestore document ID of the plan to buy
     * @param amount – amount to invest
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
            // 0) Find account doc by custom userId
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

            // 1) Load user doc to get referralCode
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

            // 2) Check referrer's account & active plan (flat userPlans collection)
            var refAccountId: String? = null
            var refUserId: String? = null
            var isRefActive = false
            if (refCode != null) {
                val refAcctQ = db.collection("accounts")
                    .whereEqualTo("userId", refCode)
                    .limit(1)
                    .get().await()
                if (!refAcctQ.isEmpty) {
                    val refAcctDoc = refAcctQ.documents.first()
                    refAccountId = refAcctDoc.id
                    refUserId = refAcctDoc.getString("userId") // should equal refCode
                    Log.d("BuyPlanRepo", "referrer accountId='$refAccountId', refUserId='$refUserId'")

                    // because userPlans is now a flat collection we check it here
                    val planQ = db.collection("userPlans")
                        .whereEqualTo("accountId", refAccountId)
                        .whereEqualTo("status", "active")
                        .limit(1)
                        .get().await()
                    isRefActive = !planQ.isEmpty
                    Log.d("BuyPlanRepo", "isRefActive=$isRefActive for referrerAcct='$refAccountId'")
                }
            }

            // 3) Transaction: debit purchaser, credit referral (if eligible), create plan & audit
            return@withContext db.runTransaction { tr ->
                // a) Load plan metadata
                val pkgRef = db.collection("plans").document(pkgId)
                val pkgSnap = tr.get(pkgRef)
                if (!pkgSnap.exists()) {
                    Log.e("BuyPlanRepo", "FAILURE: Plan not found pkgId='$pkgId'")
                    return@runTransaction Status.FAILURE
                }
                val minInv = pkgSnap.getDouble("minAmount") ?: 0.0
                val roiPct = pkgSnap.getDouble("dailyPercentage") ?: 0.0
                val dirPct = pkgSnap.getDouble("directProfit") ?: 0.0
                val payoutPct = pkgSnap.getDouble("totalPayout") ?: return@runTransaction Status.FAILURE

                // b) Minimum investment check
                if (amount < minInv) {
                    Log.e("BuyPlanRepo", "MIN INVEST ERROR: amount=$amount < minInv=$minInv")
                    return@runTransaction Status.MIN_INVEST_ERROR
                }

                // c) Check purchaser balances (currentBalance mandatory, remainingBalance optional)
                val buyerAccRef = db.collection("accounts").document(accountId)
                val buyerSnap = tr.get(buyerAccRef)

                val curBalNum = (buyerSnap.get("investment") as? Map<*, *>)?.get("currentBalance") as? Number?
                val curBal = curBalNum?.toDouble() ?: 0.0

                val remBalNum = (buyerSnap.get("investment") as? Map<*, *>)?.get("remainingBalance") as? Number?
                val remBal: Double? = remBalNum?.toDouble()

                Log.d("BuyPlanRepo", "Balances for accountId='$accountId' -> current=$curBal, remaining=$remBal")

                // Primary rule: currentBalance must be >= amount (do not allow negative)
                if (curBal < amount) {
                    Log.e("BuyPlanRepo", "INSUFFICIENT_BALANCE: current=$curBal, required=$amount")
                    return@runTransaction Status.INSUFFICIENT_BALANCE
                }

                // If remainingBalance exists and your business requires it to cover investment, enforce it too
                // (If you prefer different behavior, change this check)
                if (remBal != null && remBal < amount) {
                    Log.e("BuyPlanRepo", "INSUFFICIENT_BALANCE: remaining=$remBal, required=$amount")
                    return@runTransaction Status.INSUFFICIENT_BALANCE
                }

                // d) Credit referral bonus if eligible
                val isReferralReceivesDirectProfit = (isRefActive && refAccountId != null)
                if (isReferralReceivesDirectProfit && refAccountId != null) {
                    val bonus = amount * dirPct / 100.0
                    tr.update(
                        db.collection("accounts").document(refAccountId),
                        Paths.REFERRAL_PROFIT,
                        FieldValue.increment(bonus)
                    )
                    Log.d("BuyPlanRepo", "Credited referral bonus=$bonus to referrerAcct='$refAccountId'")
                } else {
                    Log.d("BuyPlanRepo", "No referral credit: isRefActive=$isRefActive, refAccountId=$refAccountId")
                }

                // e) Debit purchaser balance(s) — keep them >= 0
                // Update currentBalance
                tr.update(
                    buyerAccRef,
                    Paths.INVEST_CUR_BAL,
                    FieldValue.increment(-amount)
                )
                Log.d("BuyPlanRepo", "Debited amount=$amount from investment.currentBalance for accountId='$accountId'")

                // If remainingBalance exists, update it similarly
                if (remBal != null) {
                    tr.update(
                        buyerAccRef,
                        Paths.INVEST_REMAINING_BAL,
                        FieldValue.increment(-amount)
                    )
                    Log.d("BuyPlanRepo", "Debited amount=$amount from investment.remainingBalance for accountId='$accountId'")
                }

                // f) Create userPlan entry in flat 'userPlans' collection
                val upRef = db.collection("userPlans").document()
                tr.set(
                    upRef,
                    mapOf(
                        "pkgId" to pkgId,
                        "principal" to amount,
                        "roiPercent" to roiPct,
                        "roiAmount" to ((amount * roiPct) / 100.0),
                        "totalPayoutAmount" to ((amount * payoutPct) / 100.0),
                        "uplineReferralBonus" to ((amount * dirPct) / 100.0),
                        "status" to "active",
                        "buyDate" to FieldValue.serverTimestamp(),
                        "totalAccumulated" to 0.0,
                        "lastCollectedDate" to FieldValue.serverTimestamp(),
                        // NEW: referral tracking
                        "referrerId" to (refUserId ?: ""), // empty string if not present; change to null if you prefer
                        "referralReceivedDirectProfit" to isReferralReceivesDirectProfit,
                        // traceability
                        "userId" to uid,
                        "accountId" to accountId
                    )
                )
                Log.d("BuyPlanRepo", "Created userPlan doc=${upRef.id} for accountId='$accountId' and uid='$uid' (refReceived=$isReferralReceivesDirectProfit)")

                // g) Audit log
                tr.set(
                    db.collection("transactions").document(),
                    mapOf(
                        "uid" to uid,
                        "type" to "Plan Purchase",
                        "amount" to amount,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "planId" to pkgId,
                        "referrerId" to (refUserId ?: "")
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
