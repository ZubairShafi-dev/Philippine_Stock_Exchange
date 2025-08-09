package com.pse.pse.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class BuyPlanRepo(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    enum class Status { SUCCESS, MIN_INVEST_ERROR, INSUFFICIENT_BALANCE, FAILURE }

    companion object {
        private const val TAG = "BuyPlanRepo"
    }

    private object Paths {
        val INVEST_CUR_BAL = FieldPath.of("investment", "currentBalance")
        val INVEST_REMAINING_BAL = FieldPath.of("investment", "remainingBalance")
        val REFERRAL_PROFIT = FieldPath.of("earnings", "referralProfit")
    }

    /**
     * @param uid     – your custom userId (e.g., "C6756"), stored in accounts.userId and users.uid
     * @param pkgId   – Firestore doc ID of the plan to buy
     * @param amount  – amount to invest
     */
    suspend fun buyPlan(
        uid: String,
        pkgId: String,
        amount: Double
    ): Status = withContext(Dispatchers.IO) {
        val trace = UUID.randomUUID().toString().take(8)
        val startNs = System.nanoTime()
        Log.d(TAG, "[$trace] buyPlan() START uid='$uid', pkgId='$pkgId', amount=$amount")

        if (uid.isBlank() || pkgId.isBlank() || amount <= 0) {
            Log.e(
                TAG,
                "[$trace] Arg validation FAILED -> uidBlank=${uid.isBlank()}, pkgIdBlank=${pkgId.isBlank()}, amount=$amount"
            )
            return@withContext Status.FAILURE
        }

        try {
            // --- Pre-transaction resolution (queries cannot run inside the transaction) ---
            Log.d(TAG, "[$trace] Resolving buyer account by accounts.userId == '$uid'")
            val buyerAcctQ = db.collection("accounts")
                .whereEqualTo("userId", uid)
                .limit(1)
                .get().await()
            if (buyerAcctQ.isEmpty) {
                Log.e(TAG, "[$trace] FAILURE: No account found for userId='$uid'")
                return@withContext Status.FAILURE
            }
            val buyerAccRef = buyerAcctQ.documents.first().reference
            Log.d(
                TAG,
                "[$trace] Buyer account resolved -> path='${buyerAccRef.path}', id='${buyerAccRef.id}'"
            )

            Log.d(
                TAG,
                "[$trace] Resolving buyer user by users.uid == '$uid' (to read referralCode)"
            )
            val buyerUserQ = db.collection("users")
                .whereEqualTo("uid", uid)
                .limit(1)
                .get().await()
            if (buyerUserQ.isEmpty) {
                Log.e(TAG, "[$trace] FAILURE: No users doc for uid='$uid'")
                return@withContext Status.FAILURE
            }
            val buyerUserSnap = buyerUserQ.documents.first()
            val refCode: String? =
                buyerUserSnap.getString("referralCode")?.takeIf { it.isNotBlank() }
            Log.d(
                TAG,
                "[$trace] Buyer users doc='${buyerUserSnap.reference.path}', referralCode='${refCode ?: ""}'"
            )

            // Referrer (optional)
            var refAcctRef = null as com.google.firebase.firestore.DocumentReference?
            var refUserRef = null as com.google.firebase.firestore.DocumentReference?
            if (refCode != null) {
                Log.d(TAG, "[$trace] Resolving referrer account by accounts.userId == '$refCode'")
                val qAcc =
                    db.collection("accounts").whereEqualTo("userId", refCode).limit(1).get().await()
                refAcctRef = if (qAcc.isEmpty) null else qAcc.documents.first().reference
                Log.d(
                    TAG,
                    "[$trace] Referrer account ${if (refAcctRef == null) "NOT FOUND" else "resolved path='${refAcctRef.path}', id='${refAcctRef.id}'"}"
                )

                Log.d(TAG, "[$trace] Resolving referrer user by users.uid == '$refCode'")
                val qUsr =
                    db.collection("users").whereEqualTo("uid", refCode).limit(1).get().await()
                refUserRef = if (qUsr.isEmpty) null else qUsr.documents.first().reference
                Log.d(
                    TAG,
                    "[$trace] Referrer user ${if (refUserRef == null) "NOT FOUND" else "resolved path='${refUserRef.path}', id='${refUserRef.id}'"}"
                )
            } else {
                Log.d(TAG, "[$trace] No referralCode on buyer; skipping referrer resolution")
            }

            val planRef = db.collection("plans").document(pkgId)
            Log.d(TAG, "[$trace] Plan ref prepared -> path='${planRef.path}', id='${planRef.id}'")

            // ------------------------------ ATOMIC TRANSACTION ------------------------------
            val txStartNs = System.nanoTime()
            Log.d(TAG, "[$trace] TRANSACTION BEGIN")

            val result = db.runTransaction { tr ->
                // --- Read plan ---
                val planSnap = tr.get(planRef)
                if (!planSnap.exists()) {
                    Log.e(TAG, "[$trace] TX: FAIL plan not found: pkgId='$pkgId'")
                    return@runTransaction Status.FAILURE
                }
                val minInv = planSnap.getDouble("minAmount") ?: 0.0
                val roiPct = planSnap.getDouble("dailyPercentage") ?: 0.0
                val dirPct = planSnap.getDouble("directProfit") ?: 0.0
                val payoutPct = planSnap.getDouble("totalPayout") ?: run {
                    Log.e(TAG, "[$trace] TX: FAIL plan.totalPayout missing")
                    return@runTransaction Status.FAILURE
                }
                Log.d(
                    TAG,
                    "[$trace] TX: Plan fields -> minAmount=$minInv, dailyPercentage=$roiPct, directProfit=$dirPct, totalPayout=$payoutPct"
                )

                // --- Validate amount vs min ---
                if (amount < minInv) {
                    Log.e(TAG, "[$trace] TX: MIN_INVEST_ERROR -> amount=$amount < minInv=$minInv")
                    return@runTransaction Status.MIN_INVEST_ERROR
                }

                // --- Read buyer balances ---
                val buyerAccSnap = tr.get(buyerAccRef)
                val invMap = buyerAccSnap.get("investment") as? Map<*, *>
                val curBal = (invMap?.get("currentBalance") as? Number)?.toDouble()
                val remBal = (invMap?.get("remainingBalance") as? Number)?.toDouble()
                Log.d(
                    TAG,
                    "[$trace] TX: Buyer balances read -> currentBalance=$curBal, remainingBalance=$remBal (required=$amount)"
                )

                if (curBal == null || remBal == null) {
                    Log.e(
                        TAG,
                        "[$trace] TX: INSUFFICIENT_BALANCE -> missing one or both balances (cur=$curBal, rem=$remBal)"
                    )
                    return@runTransaction Status.INSUFFICIENT_BALANCE
                }
                if (curBal < amount || remBal < amount) {
                    Log.e(
                        TAG,
                        "[$trace] TX: INSUFFICIENT_BALANCE -> cur=$curBal, rem=$remBal, required=$amount"
                    )
                    return@runTransaction Status.INSUFFICIENT_BALANCE
                }

                // --- Referrer eligibility (users.status == "active") ---
                var refActive = false
                var refUid: String? = null
                if (refUserRef != null && refAcctRef != null) {
                    val refUserSnap = tr.get(refUserRef)
                    val status = refUserSnap.getString("status") ?: "inactive"
                    refActive = (status == "active")
                    refUid = refUserSnap.getString("uid") // equals refCode
                    Log.d(
                        TAG,
                        "[$trace] TX: Referrer read -> user='${refUserRef.path}', status='$status', active=$refActive, refUid='$refUid', refAcct='${refAcctRef.path}'"
                    )
                } else {
                    Log.d(
                        TAG,
                        "[$trace] TX: Referrer missing -> refUserRef=${refUserRef != null}, refAcctRef=${refAcctRef != null} (bonus will be skipped)"
                    )
                }

                // --- Compute bonus & derived plan fields ---
                val bonus = amount * dirPct / 100.0
                val roiAmount = (amount * roiPct / 100.0)
                val totalPayoutAmount = (amount * payoutPct / 100.0)
                Log.d(
                    TAG,
                    "[$trace] TX: Computed -> bonus=$bonus (amount*$dirPct/100), roiAmount=$roiAmount, totalPayoutAmount=$totalPayoutAmount"
                )

                // --- Debit buyer (both balances) ---
                tr.update(
                    buyerAccRef,
                    Paths.INVEST_CUR_BAL, FieldValue.increment(-amount),
                    Paths.INVEST_REMAINING_BAL, FieldValue.increment(-amount)
                )
                Log.d(
                    TAG,
                    "[$trace] TX: Debited buyer '${buyerAccRef.path}' -> -$amount from current & remaining"
                )

                // --- Credit referrer (optional) + transaction for referrer ---
                if (refActive && refAcctRef != null && refUid != null) {
                    tr.update(
                        refAcctRef,
                        Paths.REFERRAL_PROFIT, FieldValue.increment(bonus),
                        Paths.INVEST_CUR_BAL, FieldValue.increment(bonus),
                        Paths.INVEST_REMAINING_BAL, FieldValue.increment(bonus)
                    )
                    Log.d(
                        TAG,
                        "[$trace] TX: Credited referrer '${refAcctRef.path}' -> +$bonus to earnings.referralProfit & investment balances"
                    )

                    val refTxRef = db.collection("transactions").document()
                    tr.set(
                        refTxRef,
                        mapOf(
                            "uid" to refUid,
                            "type" to "Direct Profit",
                            "amount" to bonus,
                            "sourceUid" to uid,
                            "planId" to pkgId,
                            "timestamp" to FieldValue.serverTimestamp(),
                            "accountId" to refAcctRef.id,
                            "percentage" to dirPct
                        )
                    )
                    Log.d(
                        TAG,
                        "[$trace] TX: Created referrer transaction '${refTxRef.path}' -> type='Direct Profit', amount=$bonus, percentage=$dirPct, sourceUid='$uid'"
                    )
                } else {
                    Log.d(
                        TAG,
                        "[$trace] TX: Skipping referrer credit -> active=$refActive, refAcctRef=${refAcctRef != null}, refUid=${refUid != null}"
                    )
                }

                // --- Create userPlan doc ---
                val upRef = db.collection("userPlans").document()
                tr.set(
                    upRef,
                    mapOf(
                        "pkgId" to pkgId,
                        "principal" to amount,
                        "roiPercent" to roiPct,
                        "roiAmount" to roiAmount,
                        "totalPayoutAmount" to totalPayoutAmount,
                        "uplineReferralBonus" to bonus,
                        "status" to "active",
                        "buyDate" to FieldValue.serverTimestamp(),
                        "totalAccumulated" to 0.0,
                        "lastCollectedDate" to FieldValue.serverTimestamp(),
                        "referrerId" to (refUid ?: ""),
                        "referralReceivedDirectProfit" to (refActive && refAcctRef != null),
                        "userId" to uid,
                        "accountId" to buyerAccRef.id
                    )
                )
                Log.d(
                    TAG,
                    "[$trace] TX: Created userPlan '${upRef.path}' for userId='$uid' (referrerId='${refUid ?: ""}', referralReceived=${refActive && refAcctRef != null})"
                )

                // --- Buyer audit transaction ---
                val buyerTxRef = db.collection("transactions").document()
                tr.set(
                    buyerTxRef,
                    mapOf(
                        "uid" to uid,
                        "type" to "Plan Purchase",
                        "amount" to amount,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "planId" to pkgId,
                        "referrerId" to (refUid ?: ""),
                        "accountId" to buyerAccRef.id
                    )
                )
                Log.d(
                    TAG,
                    "[$trace] TX: Created buyer transaction '${buyerTxRef.path}' -> type='Plan Purchase', amount=$amount, referrerId='${refUid ?: ""}'"
                )

                Log.d(
                    TAG,
                    "[$trace] TX: All writes staged successfully; returning SUCCESS (will commit)"
                )
                Status.SUCCESS
            }.await()

            val txMs = (System.nanoTime() - txStartNs) / 1_000_000.0
            Log.d(TAG, "[$trace] TRANSACTION END status=$result, duration=${"%.2f".format(txMs)}ms")

            val totalMs = (System.nanoTime() - startNs) / 1_000_000.0
            Log.d(
                TAG,
                "[$trace] buyPlan() END status=$result, totalDuration=${"%.2f".format(totalMs)}ms"
            )
            result
        } catch (e: Exception) {
            val totalMs = (System.nanoTime() - startNs) / 1_000_000.0
            Log.e(TAG, "[$trace] buyPlan() EXCEPTION after ${"%.2f".format(totalMs)}ms", e)
            Status.FAILURE
        }
    }
}