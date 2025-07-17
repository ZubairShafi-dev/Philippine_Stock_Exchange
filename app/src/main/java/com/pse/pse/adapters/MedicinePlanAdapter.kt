package com.pse.pse.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pse.pse.R
import com.pse.pse.databinding.ItemMedicineInvestmentBinding
import com.pse.pse.models.PlanModel

class MedicinePlanAdapter(
    medicinePlanList: List<PlanModel>,
    private val onItemClick: (PlanModel) -> Unit
) : RecyclerView.Adapter<MedicinePlanAdapter.PlanViewHolder>() {

    val filteredListed = medicinePlanList.filter { it.type == "Medicine" }
    private val imageList = listOf(
        R.drawable.medicinew,
        R.drawable.medicine1,
        R.drawable.medicine3,
        R.drawable.medicine5,

        R.drawable.medicine7,
        R.drawable.medicine8,

        )

    inner class PlanViewHolder(val binding: ItemMedicineInvestmentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(plan: PlanModel) {
            binding.tvName.text = plan.planName
            binding.tvPrice.text = "$${"%.1f".format(plan.minAmount)}"
            binding.tvChange.text = "+${"%.2f".format(plan.dailyPercentage)}%"
            binding.tvSymbol.text = plan.type
            binding.planDays.text = plan.planDays.toString()
            val randomImage = imageList.random()
            binding.imgLogo.setImageResource(randomImage)
            binding.root.setOnClickListener {
                onItemClick(plan)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val binding = ItemMedicineInvestmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PlanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {

        holder.bind(filteredListed[position])
    }

    override fun getItemCount(): Int = filteredListed.size
}
