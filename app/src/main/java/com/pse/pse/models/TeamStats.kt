package com.pse.pse.models

/**
 * activeMembers / directBusiness / groupSell are always zero
 * for Team-Rankings, but left here in case other screens read them.
 */
data class TeamStats(
    val currentInvestment: Double,
    val activeMembers: Int = 0,
    val directBusiness: Double = 0.0,
    val groupSell: Double = 0.0,
    val unlockedLevels: List<Int>
)