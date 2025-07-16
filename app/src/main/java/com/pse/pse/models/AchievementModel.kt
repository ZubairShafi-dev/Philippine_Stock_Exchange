package com.pse.pse.models

import com.google.firebase.Timestamp

data class AchievementModel(
    val rankName: String = "",
    val userId: String = "",
    val collectedAt: Timestamp= Timestamp.now(),
    val rewardAmount: Double = 0.0
)
