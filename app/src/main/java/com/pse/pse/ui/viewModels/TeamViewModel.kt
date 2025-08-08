package com.pse.pse.ui.viewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pse.pse.data.repository.TeamRepository
import com.pse.pse.models.TeamLevelStatus
import com.pse.pse.models.TeamStats
import kotlinx.coroutines.launch

class TeamViewModel : ViewModel() {

    private val teamRepository = TeamRepository()

    private val _teamLevelsWithStats = MutableLiveData<List<TeamLevelStatus>>()
    val teamLevelsWithStats: LiveData<List<TeamLevelStatus>> = _teamLevelsWithStats

    private val _teamStats = MutableLiveData<TeamStats>()
    val teamStats: LiveData<TeamStats> get() = _teamStats

    private val _rewardResult = MutableLiveData<Boolean>()
    val rewardResult: LiveData<Boolean> get() = _rewardResult

    val claimedRewards = mutableSetOf<Int>()

    fun loadEverything(userId: String) = viewModelScope.launch {
        try {
            val (levels, meta) = teamRepository.fetchLevelsAndMaybeCredit(userId)

            _teamLevelsWithStats.postValue(levels)

            if (meta.booked) {
                Log.d("TeamVM", "✅ Team profit +${meta.creditedAmount} credited for $userId")
            } else {
                Log.d("TeamVM", "ℹ️  No profit booked today for $userId")
            }
        } catch (e: Exception) {
            _teamLevelsWithStats.postValue(emptyList())
            Log.e("TeamVM", "❌ Cloud call failed", e)
        }
    }

    fun fetchTeamStats(userId: String) {
        viewModelScope.launch {
            try {
                val result = teamRepository.calculateTeamRanking(userId)
                _teamStats.postValue(result)
            } catch (e: Exception) {
                _teamStats.postValue(
                    TeamStats(0.0, 0, 0.0, 0.0, emptyList())
                )
            }
        }
    }
}