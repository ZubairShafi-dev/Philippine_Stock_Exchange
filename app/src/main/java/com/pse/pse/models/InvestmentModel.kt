package com.pse.pse.models

data class InvestmentModel(
    val totalDeposit: Double=0.0,     // Total amount deposited by the user
    val remainingBalance: Double=0.0, // The remaining balance in the account
    val currentBalance: Double=0.0,
)     // The profit earned from staking
{
    fun toMap(): Map<String, Any> {
        return mapOf(
            "totalDeposit" to totalDeposit,
            "remainingBalance" to remainingBalance,
            "currentBalance" to currentBalance
        )
    }
}