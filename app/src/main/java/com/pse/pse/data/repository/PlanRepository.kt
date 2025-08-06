package com.yourpackage.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pse.pse.models.Plan
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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
                val plans = snap!!.documents.mapNotNull { it.toObject(Plan::class.java)?.apply { docId = it.id } }
                trySend(plans)
            }
        awaitClose { reg.remove() }
    }

    /** One‑shot read for legacy callers (LiveData) */
    fun getPlansOnce(): LiveData<List<Plan>> {
        val live = MutableLiveData<List<Plan>>()
        db.collection("plans").get()
            .addOnSuccessListener { res ->
                live.postValue(res.mapNotNull { it.toObject(Plan::class.java).apply { docId = it.id } })
            }
            .addOnFailureListener { live.postValue(emptyList()) }
        return live
    }
}
