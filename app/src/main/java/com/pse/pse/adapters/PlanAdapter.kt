package com.pse.pse.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.pse.pse.R
import com.pse.pse.models.Plan

class PlanAdapter(
    private val onInvestClick: (Plan) -> Unit
) : ListAdapter<Plan, PlanAdapter.PlanViewHolder>(DiffCallback()) {

    inner class PlanViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val planName = itemView.findViewById<TextView>(R.id.coinName)
        private val minAmount = itemView.findViewById<TextView>(R.id.coinUnit)
        private val totalPayout = itemView.findViewById<TextView>(R.id.coinPrice)
        private val dailyPercentage = itemView.findViewById<TextView>(R.id.coinPercent)
        private val investBtn = itemView.findViewById<MaterialButton>(R.id.investButton)

        fun bind(plan: Plan) {
            planName.text = plan.planName
            minAmount.text = "Min: Rs. ${plan.minAmount}"
            totalPayout.text = "Total Payout: Rs. ${plan.totalPayout}"
            dailyPercentage.text = "Daily ROI: ${"%.2f".format(plan.dailyPercentage)}%"

            investBtn.setOnClickListener {
                onInvestClick(plan)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plan, parent, false)
        return PlanViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Plan>() {
        override fun areItemsTheSame(oldItem: Plan, newItem: Plan): Boolean = oldItem.docId == newItem.docId
        override fun areContentsTheSame(oldItem: Plan, newItem: Plan): Boolean = oldItem == newItem
    }
}
