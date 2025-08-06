package com.yourpackage.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pse.pse.models.Plan
import com.yourpackage.data.repository.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel bridges PlanRepository → UI layer.
 * Exposes [UiState] so the Fragment can show loading / error / data.
 */
class PlansViewModel(
    private val repo: PlanRepository = PlanRepository()
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class Success(val plans: List<Plan>) : UiState()
        data class Error(val throwable: Throwable) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    init {
        // Collect real‑time plans and push to UI state
        viewModelScope.launch {
            try {
                repo.streamPlans().collectLatest { plans ->
                    _state.value = UiState.Success(plans)
                }
            } catch (e: Exception) {
                _state.value = UiState.Error(e)
            }
        }
    }
}
