package com.pse.pse.models

data class SalaryProfile(
    val userId: String = "",
    val status: String = "",                 // window_open | active | ended
    val windowStart: com.google.firebase.Timestamp? = null,
    val windowEnd: com.google.firebase.Timestamp? = null,
    val snapshotDirectBusiness: Double = 0.0,
    val tier: Int = 0,
    val salaryAmount: Double = 0.0,
    val nextPayoutAt: com.google.firebase.Timestamp? = null,
    val lastPayoutAt: com.google.firebase.Timestamp? = null,
    val reason: String? = null
)

data class EnsureSalaryResult(
    val ok: Boolean = true,
    val existed: Boolean = false
)
