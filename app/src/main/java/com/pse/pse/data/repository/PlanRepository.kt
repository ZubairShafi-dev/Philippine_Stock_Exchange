package com.pse.pse.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pse.pse.models.PlanModel
import com.pse.pse.utils.Constants

class PlanRepository {


    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private var planListener: ListenerRegistration? = null



    fun     getPlans(): LiveData<List<PlanModel>> {
        val plansLiveData = MutableLiveData<List<PlanModel>>()

        planListener = db.collection(Constants.PLAN_COLLECTION)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    plansLiveData.value = emptyList()
                    return@addSnapshotListener
                }
                val recitations = snapshots?.documents?.mapNotNull { document ->
                    document.toObject(PlanModel::class.java)
                }
                plansLiveData.value = recitations ?: emptyList()
            }
        return plansLiveData
    }
}