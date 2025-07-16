package com.trustledger.aitrustledger.viewModel.Adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.trustledger.aitrustledger.R
import com.trustledger.aitrustledger.databinding.ItemStockInvestmentBinding
import com.pse.pse.models.PlanModel

class StocksPlanAdapter(
    private val stockPlanList: List<PlanModel>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<StocksPlanAdapter.PlanViewHolder>() {
    private val imageList = listOf(

        R.drawable.stock1,
        R.drawable.stock2,
        R.drawable.stock3,
        R.drawable.stock4,
        R.drawable.stock5,
        R.drawable.stock6,
        )
    interface OnItemClickListener {
        fun onPlanClick(plan: PlanModel)
    }

    inner class PlanViewHolder(val binding: ItemStockInvestmentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(plan: PlanModel) {
            binding.tvName.text = plan.planName
            binding.tvPrice.text = "$${"%.1f".format(plan.minAmount)}"
            binding.tvChangeProfit.text = "+${"%.2f".format(plan.dailyPercentage)}%"
            binding.tvSymbol.text = plan.type
            val randomImage = imageList.random()
            binding.imgLogoStock.setImageResource(randomImage)
            binding.root.setOnClickListener {
                listener.onPlanClick(plan)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val binding = ItemStockInvestmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PlanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        val filterList = stockPlanList.filter { it.type == "Stocks" }
        holder.bind(filterList[position])
    }

    override fun getItemCount(): Int = stockPlanList.filter { it.type == "Stocks" }.size
}
