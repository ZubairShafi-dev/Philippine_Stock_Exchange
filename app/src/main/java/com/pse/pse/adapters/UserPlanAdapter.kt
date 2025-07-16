package com.trustledger.aitrustledger.viewModel.Adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.trustledger.aitrustledger.R
import com.trustledger.aitrustledger.databinding.ItemStockInvestmentBinding
import com.pse.pse.models.UserPlanModel

class UserPlanAdapter(
    plans: List<UserPlanModel>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<UserPlanAdapter.PlanViewHolder>() {
    private val imageList = listOf(

        R.drawable.stock1,
        R.drawable.stock2,
        R.drawable.stock3,
        R.drawable.stock4,
        R.drawable.stock5,
        R.drawable.stock6,
    )

    private val stockBoughtList = plans.filter { it.status.equals("stock_open", ignoreCase = true) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val binding = ItemStockInvestmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        holder.bind(stockBoughtList[position])
    }

    override fun getItemCount(): Int = stockBoughtList.size

    interface OnItemClickListener {
        fun onPlanClick(plan: UserPlanModel)
    }

    inner class PlanViewHolder(val binding: ItemStockInvestmentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(plan: UserPlanModel) {
            binding.tvName.text = plan.plan_name
            binding.tvPrice.text = "$${"%.1f".format(plan.invested_amount)}"
            binding.tvChangeProfit.text = "+${"%.1f".format(plan.profitTrack)}"
            binding.tvSymbol.text = plan.plan_name
            val randomImage = imageList.random()
            binding.imgLogoStock.setImageResource(randomImage)
            binding.root.setOnClickListener {
                listener.onPlanClick(plan)
            }
        }
    }
}
