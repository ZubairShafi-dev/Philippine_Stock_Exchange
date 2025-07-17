package com.pse.pse.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pse.pse.R
import com.pse.pse.databinding.ItemForexInvestmentBinding
import com.pse.pse.models.UserPlanModel

class BoughtForexAdapter(
    private val plans: List<UserPlanModel>,
    private val onInvestClick: (UserPlanModel) -> Unit
) : RecyclerView.Adapter<BoughtForexAdapter.PlanViewHolder>() {

    private val imageList = listOf(

        R.drawable.dogecoin,
        R.drawable.cardano,
        R.drawable.ic_ethereum,
        R.drawable.ic_star,
        R.drawable.img_1,
    )


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PlanViewHolder {
        val binding = ItemForexInvestmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlanViewHolder(binding)

    }

    override fun onBindViewHolder(
        holder: PlanViewHolder,
        position: Int
    ) {
       holder.bind(plans[position])
    }

    override fun getItemCount(): Int =plans.size

    inner class PlanViewHolder(val binding: ItemForexInvestmentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: UserPlanModel){
            binding.coinName.text = item.plan_name
            binding.coinPrice.text = "$${"%.1f".format(item.invested_amount)}"
            binding.coinPercent.text = "+${"%.2f".format(item.daily_profit)}%"
            binding.investButton.text = "Sell"
            binding.investButton.setOnClickListener {
                onInvestClick(item)

            }
            val randomImage = imageList.random()
            binding.coinIcon.setImageResource(randomImage)
        }

    }
}