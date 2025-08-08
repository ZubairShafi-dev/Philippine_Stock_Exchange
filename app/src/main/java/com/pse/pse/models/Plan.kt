package com.pse.pse.models

import com.google.firebase.Timestamp

data class Plan(
    val planName: String = "",
    val minAmount: Double = 0.0,
    val maxAmount: Double? = 0.0,
    var dailyPercentage: Double = 0.0,
    var directProfit: Double = 0.0,
    var totalPayout: Double = 0.0,
    val timestamp: Timestamp? = null,
    var docId: String = ""
)
