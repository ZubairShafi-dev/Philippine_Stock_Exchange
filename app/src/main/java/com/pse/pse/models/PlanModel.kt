package com.pse.pse.models

import com.google.firebase.Timestamp

data class PlanModel(
    var dailyPercentage: Double? = null,
    var directProfit: Double? = null,
    var id: Double? = null,
    var minAmount: Double? = null,
    var planDays: Int? = null,
    var planName: String? = null,
    var type: String? = null,
    var timestamp: Timestamp? = null
) {
    // Empty constructor required by Firebase
    constructor() : this(null, null, null, null, null, null, null, null)
}
