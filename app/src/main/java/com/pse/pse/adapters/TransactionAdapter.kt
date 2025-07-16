package com.trustledger.aitrustledger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trustledger.aitrustledger.R
import com.pse.pse.models.TransactionModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shows a list of transactions.
 */
class TransactionAdapter : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private var transactions: List<TransactionModel> = emptyList()

    fun setData(list: List<TransactionModel>) {
        transactions = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMonth: TextView = itemView.findViewById(R.id.tv_month)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        private val tvAmount: TextView = itemView.findViewById(R.id.tv_amount)

        private val monthFmt = SimpleDateFormat("MMMM", Locale.getDefault())
        private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        fun bind(tx: TransactionModel) {/* ---------- DATE ---------- */
            // `createdAt` is a Timestamp; fall back to now if null
            val date: Date = tx.timestamp?.toDate() ?: Date()
            tvMonth.text =
                if (tx.type == TransactionModel.TYPE_ACHIEVEMENT || tx.type == TransactionModel.TYPE_TEAM || tx.type == TransactionModel.TYPE_INVESTMENT_SOLD || tx.type == TransactionModel.TYPE_INVESTMENT_BOUGHT || tx.type == TransactionModel.TYPE_REFERRAL) {
                    "${tx.address}"
                } else {
                    monthFmt.format(date)
                }

            tvDate.text = dateFmt.format(date)

            /* ---------- STATUS ---------- */
            tvStatus.text = tx.status.replaceFirstChar { it.uppercase() }
            val colorRes = when (tx.status.lowercase(Locale.getDefault())) {
                TransactionModel.STATUS_APPROVED.lowercase() -> R.color.green
                TransactionModel.STATUS_REJECTED.lowercase() -> R.color.black
                TransactionModel.STATUS_COLLECTED.lowercase() -> R.color.collected_yellow
                TransactionModel.STATUS_BOUGHT.lowercase() -> R.color.skyblue
                TransactionModel.STATUS_SOLD.lowercase() -> R.color.green
                TransactionModel.STATUS_RECEIVED.lowercase() -> R.color.green

                else -> R.color.bright_red    // pending
            }
            tvStatus.setTextColor(itemView.context.getColor(colorRes))

            /* ---------- AMOUNT ---------- */
            tvAmount.text = "$${"%,.2f".format(Locale.getDefault(), tx.amount)}"
        }
    }
}