package com.pse.pse.models

/**
 * Only `minInvestment` matters for ranking now, but the other
 * fields stay (defaulted) so existing code compiles untouched.
 */
data class LevelCondition(
    val level: Int,
    val minInvestment: Double,
    val activeMembers: Int = 0,
    val directBusiness: Double = 0.0,
    val groupSell: Double = 0.0
)