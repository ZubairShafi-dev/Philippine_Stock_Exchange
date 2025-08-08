package com.yourpackage.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pse.pse.models.Plan
import com.yourpackage.data.repository.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * ViewModel bridges PlanRepository → UI layer.
 * Exposes [UiState] so the Fragment can show loading / error / data.
 */
class PlansViewModel(
    private val repo: PlanRepository          // repo manually inject hoga
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class Success(val plans: List<Plan>) : UiState()
        data class Error(val msg: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    init {
        subscribePlans()
    }

    private fun subscribePlans() = viewModelScope.launch {
        repo.streamPlans()
            .onStart { _state.value = UiState.Loading }
            .catch { e -> _state.value = UiState.Error(e.message ?: "Unknown error") }
            .collectLatest { list ->
                _state.value = UiState.Success(list)
            }
    }

    fun refresh() = subscribePlans()
}

