package com.pse.pse.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Plan(
    val planName: String = "",
    val minAmount: Double = 0.0,
    val maxAmount: Double = 0.0,
    var roiPercent: Double = 0.0,
    var directPercent: Double = 0.0,
    var totalPayoutPercent: Double = 0.0,
    val timestamp: Timestamp? = null,
    var docId: String = ""
)
