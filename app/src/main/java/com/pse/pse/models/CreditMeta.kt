package com.pse.pse.models

/** Describes what the Cloud Function did with today’s profit. */
data class CreditMeta(
    val booked: Boolean,      // true ⇢ profit credited today
    val creditedAmount: Double
)

