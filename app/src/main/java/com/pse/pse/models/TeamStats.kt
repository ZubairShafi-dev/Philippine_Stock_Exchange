package com.pse.pse.models

data class TeamStats(
    val currentInvestment: Double,
    val activeMembers: Int,
    val directBusiness: Double,
    val groupSell: Double,
    val unlockedLevels: List<Int>
)
