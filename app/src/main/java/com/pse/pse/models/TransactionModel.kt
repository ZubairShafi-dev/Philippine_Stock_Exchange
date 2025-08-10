package com.pse.pse.models

import com.google.firebase.Timestamp

/**
 * Data model representing a deposit or withdrawal transaction.
 */
// com.pse.pse.models.TransactionModel
// com/pse/pse/models/TransactionModel.kt
data class TransactionModel(
    val rankName: String = "",
    val transactionId: String = "",
    val userId: String = "",
    val amount: Double = 0.0,
    val type: String = TYPE_WITHDRAW,
    val address: String = "",
    val status: String = STATUS_PENDING,
    val balanceUpdated: Boolean = false,
    val timestamp: com.google.firebase.Timestamp? = com.google.firebase.Timestamp.now()
) {
    fun toMap(): Map<String, Comparable<*>?> = mapOf(
        FIELD_ID to transactionId,
        FIELD_USER_ID to userId,
        FIELD_AMOUNT to amount,
        FIELD_TYPE to type,
        FIELD_ADDRESS to address,
        FIELD_STATUS to status,
        FIELD_BALANCE_UPDATED to balanceUpdated,
        FIELD_TIMESTAMP to timestamp
    )

    companion object {
        // Firestore field names
        const val FIELD_ID = "transactionId"
        const val FIELD_USER_ID = "userId"
        const val FIELD_AMOUNT = "amount"
        const val FIELD_TYPE = "type"
        const val FIELD_ADDRESS = "address"
        const val FIELD_STATUS = "status"
        const val FIELD_BALANCE_UPDATED = "balanceUpdated"
        const val FIELD_TIMESTAMP = "timestamp"

        // ✅ Keep these (existing flows)
        const val TYPE_WITHDRAW = "withdraw"
        const val TYPE_DEPOSIT = "deposit"

        // ✅ New types from your current backend
        const val TYPE_DAILY_ROI = "dailyRoi"
        const val TYPE_TEAM_PROFIT = "teamProfit"
        const val TYPE_LEADERSHIP = "leadership_bonus"
        const val TYPE_SALARY = "salary"
        const val TYPE_PLAN_PURCHASE = "Plan Purchase"  // exact string used in BuyPlanRepo
        const val TYPE_DIRECT_PROFIT = "Direct Profit"  // exact string used in BuyPlanRepo

        // Status values
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_COLLECTED = "collected"
        const val STATUS_RECEIVED = "received"
        const val STATUS_BOUGHT = "bought"
        const val STATUS_SOLD = "sold"

        // ✅ Add to support new docs
        const val STATUS_COMPLETED = "completed"
        const val STATUS_CREDITED = "credited"
    }
}
