package com.pse.pse.ui.viewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pse.pse.data.repository.TeamRepository
import com.pse.pse.models.SalaryProfile
import com.pse.pse.models.TeamLevelStatus
import com.pse.pse.models.TeamStats
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class TeamViewModel : ViewModel() {

    private val repo = TeamRepository()

    // ---------- Team Levels (unchanged) ----------
    private val _teamLevelsWithStats = MutableLiveData<List<TeamLevelStatus>>()
    val teamLevelsWithStats: LiveData<List<TeamLevelStatus>> = _teamLevelsWithStats

    fun loadEverything(userId: String) = viewModelScope.launch {
        try {
            val (levels, meta) = repo.fetchLevelsAndMaybeCredit(userId)
            _teamLevelsWithStats.postValue(levels)
            if (meta.booked) Log.d("TeamVM", "profit +${meta.creditedAmount}")
        } catch (e: Exception) {
            _teamLevelsWithStats.postValue(emptyList())
            Log.e("TeamVM", "cloud call failed", e)
        }
    }

    // ---------- Team Rankings (self-deposit only) ----------
    private val _teamStats = MutableLiveData<TeamStats>()
    val teamStats: LiveData<TeamStats> get() = _teamStats

    fun fetchTeamStats(userId: String) = viewModelScope.launch {
        try {
            _teamStats.postValue(repo.calculateTeamRanking(userId))
        } catch (e: Exception) {
            _teamStats.postValue(TeamStats(0.0, unlockedLevels = emptyList()))
        }
    }

    // ---------- Salary Program ----------
    private val _salaryProfile = MutableLiveData<SalaryProfile?>()
    val salaryProfile: LiveData<SalaryProfile?> get() = _salaryProfile

    @OptIn(FlowPreview::class)
    fun observeSalary(userId: String) = viewModelScope.launch {
        // Start listening immediately
        val flow = repo.salaryProfileFlow(userId).distinctUntilChanged().debounce(150)

        // In parallel, best-effort ensure (doesn't block UI)
        launch {
            try {
                repo.ensureSalaryProfile(userId)
            } catch (_: Exception) {
            }
        }

        flow.collectLatest { _salaryProfile.postValue(it) }
    }

    suspend fun fetchSalaryCurrentAdb(userId: String): Double? = repo.fetchSalaryCurrentAdb(userId)
}