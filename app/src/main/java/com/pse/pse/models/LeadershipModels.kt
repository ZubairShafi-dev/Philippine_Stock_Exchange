// app/src/main/java/com/pse/pse/models/LeadershipModels.kt
package com.pse.pse.models

import com.google.firebase.Timestamp

data class LeadershipTier(val members: Int, val bonus: Double)

object LeadershipTiers {
    val TIERS = listOf(
        LeadershipTier(50, 100.0),
        LeadershipTier(100, 200.0),
        LeadershipTier(500, 1500.0),
        LeadershipTier(1000, 4000.0),
        LeadershipTier(5000, 25000.0),
        LeadershipTier(10000, 60000.0),
        LeadershipTier(30000, 150000.0),
        LeadershipTier(50000, 300000.0),
    )
}

data class LeadershipProgress(
    val userId: String = "",
    val directActiveCount: Int = 0,
    val awarded: List<Int> = emptyList(), // milestone "members" ints
    val lastCheckedAt: Timestamp? = null,
    val lastAwardAmount: Double? = null
)
