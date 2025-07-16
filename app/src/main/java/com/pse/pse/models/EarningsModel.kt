package com.pse.pse.models

data class EarningsModel(
    val dailyProfit: Double=0.0,      // Profit earned daily
    val buyingProfit: Double=0.0,     // Profit made from purchases
    val referralProfit: Double=0.0,   // Referral-based profit
    val totalEarned: Double=0.0,      // Total earnings accumulated
    val teamProfit: Double=0.0
) // Profit from the user's referral team)
{
    fun toMap(): Map<String, Any> {
        return mapOf(
            "dailyProfit" to dailyProfit,
            "buyingProfit" to buyingProfit,
            "referralProfit" to referralProfit,
            "totalEarned" to totalEarned,
            "teamProfit" to teamProfit
        )
    }
}