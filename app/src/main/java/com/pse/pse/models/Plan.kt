package com.pse.pse.models

import com.google.firebase.Timestamp

data class Plan(
    val planName: String = "",
    val minAmount: Int? = 0,
    val maxAmount: Int? = 0,
    val dailyPercentage: Double = 0.0,
    val directProfit: Int = 0,
    val totalPayout: Int = 0,
    val timestamp: Timestamp? = null,
    val docId: String = ""
)
