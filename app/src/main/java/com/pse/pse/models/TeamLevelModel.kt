package com.pse.pse.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TeamLevelModel(
    val level: Int = 0,
    val requiredMembers: Int = 0,
    val profitPercentage: Double = 0.0,
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val inactiveUsers: Int = 0,
    val totalDeposit: Double = 0.0,
    val users: List<UserListModel> = emptyList()   // full user list for that level
) : Parcelable
