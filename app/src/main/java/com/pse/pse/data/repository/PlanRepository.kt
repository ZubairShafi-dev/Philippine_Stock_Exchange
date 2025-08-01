package com.pse.pse.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.pse.pse.models.Plan



class PlanRepository {

    private val firestore = FirebaseFirestore.getInstance()

    fun getPlans(): LiveData<List<Plan>> {
        val plansLiveData = MutableLiveData<List<Plan>>()

        firestore.collection("plans")
            .get()
            .addOnSuccessListener { result ->
                val planList = result.map { it.toObject(Plan::class.java) }
                plansLiveData.postValue(planList)
            }
            .addOnFailureListener {
                plansLiveData.postValue(emptyList())
            }

        return plansLiveData
    }
}
