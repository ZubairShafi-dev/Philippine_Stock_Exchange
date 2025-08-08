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

    fun initSalary(userId: String) = viewModelScope.launch {
        try {
            repo.ensureSalaryProfile(userId)  // idempotent
            _salaryProfile.postValue(repo.getSalaryProfile(userId))
        } catch (e: Exception) {
            Log.e("TeamVM", "initSalary failed", e)
            _salaryProfile.postValue(null)
        }
    }

    fun refreshSalary(userId: String) = viewModelScope.launch {
        try {
            _salaryProfile.postValue(repo.getSalaryProfile(userId))
        } catch (e: Exception) {
            Log.e("TeamVM", "refreshSalary failed", e)
        }
    }
}