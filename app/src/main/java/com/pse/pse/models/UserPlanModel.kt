package com.pse.pse.models

import com.google.firebase.Timestamp

data class UserPlanModel(
    val user_id: String = "",
    val plan_name: String = "",
    val invested_amount: Double = 0.0,
    val direct_profit: Double = 0.0,
    val daily_profit: Double = 0.0,
    val percentage: Double = 0.0,
    val directProfitPercent: Double = 0.0,
    val start_date: Timestamp = Timestamp.now(),
    val expiry_date: Timestamp,
    val status: String = "active",
    val lastCollectedDate: Timestamp = Timestamp.now(),
    val quantity: Int = 1,
    val docId: String = "",
    val profitTrack: Double
)
