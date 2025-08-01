package com.pse.pse.ui.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.pse.pse.data.repository.PlanRepository
import com.pse.pse.models.Plan

class PlansViewModel(private val repository: PlanRepository) : ViewModel() {

        private val _plans = MutableLiveData<List<Plan>>()
        val plans: LiveData<List<Plan>> get() = _plans

        fun fetchPlans() {
            repository.getPlans().observeForever {
                _plans.value = it
            }
        }


}