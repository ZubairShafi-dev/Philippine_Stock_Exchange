package com.yourpackage.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.pse.pse.models.Plan
import com.pse.pse.models.UserPlan
import com.pse.pse.models.UserPlanUi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await

/**
 * Repository that exposes investment packages from Firestore.
 *  - Re‑maps to the new [Plan] data class with ROI %, direct %, payout %.
 *  - By default returns a *hot* Flow so UI stays in sync with admin edits.
 */
class PlanRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    /** Realtime stream (Kotlin Flow) */
    fun streamPlans(): Flow<List<Plan>> = callbackFlow {
        val reg: ListenerRegistration = db.collection("plans")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    // Emit empty list on error; you may wrap in Result<> instead.
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val plans = snap!!.documents.mapNotNull {
                    it.toObject(Plan::class.java)?.apply { docId = it.id }
                }
                trySend(plans)
            }
        awaitClose { reg.remove() }
    }

    /** Stream of ALL userPlans for a user (ordered newest first). */
    fun streamUserPlans(uid: String): Flow<List<UserPlan>> = callbackFlow {
        val reg = db.collection("userPlans")
            .whereEqualTo("userId", uid)
            .orderBy("buyDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList()); return@addSnapshotListener
                }
                val list = snap.documents.mapNotNull {
                    it.toObject(UserPlan::class.java)?.apply { docId = it.id }
                }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** Helper: fetch a map of planId -> planName + meta (one-shot). */
    private suspend fun loadPlanMeta(): Map<String, Triple<String, Double?, Pair<Double?, Double?>>> {
        // Triple(planName, directPercent, Pair(minAmount, maxAmount))
        val res = db.collection("plans").get().await()
        return res.documents.associate { d ->
            val id = d.id
            val name = d.getString("planName") ?: ""
            val direct = d.getDouble("directProfit")
            val minA = d.getDouble("minAmount")
            val maxA = d.getDouble("maxAmount")
            id to Triple(name, direct, Pair(minA, maxA))
        }
    }

    /**
     * Stream UI-joined userPlans (userPlans + planName/directPercent/min/max).
     * Uses the plans stream for instant admin edits, and re-joins client-side.
     */
    fun streamUserPlansUi(uid: String): Flow<List<UserPlanUi>> {
        // Reuse the live plans stream so admin edits (names/percents) reflect.
        return streamPlans().combine(streamUserPlans(uid)) { plans, ups ->
            val meta = plans.associateBy({ it.docId ?: "" }) { p ->
                Triple(p.planName, p.directProfit, Pair(p.minAmount, p.maxAmount))
            }
            ups.map { up ->
                val m = meta[up.pkgId]
                UserPlanUi(
                    userPlan = up,
                    planName = m?.first ?: "",
                    directPercent = m?.second,
                    minAmount = m?.third?.first,
                    maxAmount = m?.third?.second
                )
            }
        }
    }
}
