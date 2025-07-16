package com.trustledger.aitrustledger.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.trustledger.aitrustledger.R
import com.trustledger.aitrustledger.databinding.ItemForexInvestmentBinding
import com.pse.pse.models.PlanModel

class ForexPlanAdapter(
    private val forexPlanList: List<PlanModel>,
    private val onItemClick: (PlanModel) -> Unit
) : RecyclerView.Adapter<ForexPlanAdapter.PlanViewHolder>() {

    private val imageList = listOf(
        R.drawable.dogecoin,
        R.drawable.cardano,
        R.drawable.ic_ethereum,
        R.drawable.ic_star,
        R.drawable.img_1,
    )

    val filteredListed = forexPlanList.filter { it.type == "Forex" }

    inner class PlanViewHolder(val binding: ItemForexInvestmentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(plan: PlanModel) {
            binding.coinName.text = plan.planName
            binding.coinPrice.text = String.format("$%.1f", plan.minAmount)
            binding.coinPercent.text = String.format("+%.2f%%", plan.dailyPercentage)
            binding.coinUnit.text = plan.directProfit.toString()
            val randomImage = imageList.random()
            binding.coinIcon.setImageResource(randomImage)
            binding.investButton.setOnClickListener {
                onItemClick(plan)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val binding = ItemForexInvestmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PlanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        holder.bind(filteredListed[position])
    }

    override fun getItemCount(): Int = filteredListed.size
}