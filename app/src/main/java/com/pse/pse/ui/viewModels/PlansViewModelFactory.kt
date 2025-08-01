package com.pse.pse.ui.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pse.pse.data.repository.PlanRepository

class PlansViewModelFactory(private val repository: PlanRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlansViewModel(repository) as T
        }


}