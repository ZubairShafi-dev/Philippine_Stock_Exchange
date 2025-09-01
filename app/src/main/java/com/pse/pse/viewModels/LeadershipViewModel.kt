// app/src/main/java/com/pse/pse/ui/viewModels/LeadershipViewModel.kt
package com.pse.pse.viewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pse.pse.data.repository.LeadershipRepository
import com.pse.pse.models.LeadershipProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LeadershipViewModel(app: Application): AndroidViewModel(app) {
    private val repo = LeadershipRepository(app)

    private val _progress = MutableStateFlow(LeadershipProgress())
    val progress: StateFlow<LeadershipProgress> = _progress

    fun start(userId: String) {
        // realtime updates
        viewModelScope.launch {
            repo.observeProgress(userId).collectLatest { _progress.value = it }
        }
        // kick a check once (no swipe needed)
        viewModelScope.launch {
            repo.ensureCheckNow(userId)
        }
    }
}