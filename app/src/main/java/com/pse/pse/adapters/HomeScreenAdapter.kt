package com.trustledger.aitrustledger.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.trustledger.aitrustledger.R
import com.trustledger.aitrustledger.databinding.ItemForexInvestmentBinding
import com.pse.pse.models.PlanModel




class HomeScreenAdapter(
    private val items: List<PlanModel>,
    private val planType: String, // This tells the adapter which type of images to use
    private val onInvestClick: (PlanModel) -> Unit
) : RecyclerView.Adapter<HomeScreenAdapter.ItemViewHolder>() {

    // Stock image list
    private val stockImageList = listOf(
        R.drawable.stock1,
        R.drawable.stock2,
        R.drawable.stock3,
        R.drawable.stock4,
        R.drawable.stock5,
        R.drawable.stock6,
    )

    // Medicine image list
    private val medicineImageList = listOf(
        R.drawable.medicinew,
        R.drawable.medicine1,
        R.drawable.medicine3,
        R.drawable.medicine5,
        R.drawable.medicine7,
        R.drawable.medicine8,
    )

    // Forex image list
    private val forexImageList = listOf(
        R.drawable.dogecoin,
        R.drawable.cardano,
        R.drawable.ic_ethereum,
        R.drawable.ic_star,
        R.drawable.img_1,
    )

    inner class ItemViewHolder(val binding: ItemForexInvestmentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PlanModel) {
            binding.coinName.text = item.planName
            binding.coinPrice.text = "$${"%.1f".format(item.minAmount)}"
            binding.coinPercent.text = "+${"%.2f".format(item.dailyPercentage)}%"
            binding.coinUnit.text = "${"%.1f".format(item.directProfit)}"

            // Set icon image based on plan type
            val imageRes = when (planType) {
                "Forex" -> forexImageList.random()
                "Stocks" -> stockImageList.random()
                "Medicine" -> medicineImageList.random()
                else -> R.drawable.ic_star // Fallback
            }
            binding.coinIcon.setImageResource(imageRes)

            binding.investButton.setOnClickListener {
                onInvestClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemForexInvestmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
