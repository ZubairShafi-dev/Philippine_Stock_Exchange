package com.pse.pse.models

import com.google.firebase.Timestamp

/**
 * Data model representing a deposit or withdrawal transaction.
 */
data class TransactionModel(
    val rankName: String = "",
    val transactionId: String = "",
    val userId: String = "",
    val amount: Double = 0.0,
    val type: String = TYPE_WITHDRAW,
    val address: String = "",
    val status: String = STATUS_PENDING,
    val balanceUpdated: Boolean = false,
    val timestamp: Timestamp? = Timestamp.now()
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

        // Transaction types
        const val TYPE_WITHDRAW = "withdraw"
        const val TYPE_DEPOSIT = "deposit"
        const val TYPE_ACHIEVEMENT = "achievement"
        const val TYPE_INVESTMENT_BOUGHT = "investmentBought"
        const val TYPE_INVESTMENT_SOLD = "investmentSold"
        const val TYPE_TEAM = "teamReward"
        const val TYPE_REFERRAL = "referralReward"

        // Status values
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_COLLECTED = "collected"
        const val STATUS_RECEIVED = "received"
        const val STATUS_BOUGHT = "bought"
        const val STATUS_SOLD = "sold"

    }
}
