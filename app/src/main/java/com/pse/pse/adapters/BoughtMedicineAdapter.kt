package com.pse.pse.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pse.pse.R
import com.pse.pse.databinding.ItemMedicineInvestmentBinding
import com.pse.pse.models.UserPlanModel

class BoughtMedicineAdapter(
    plans: List<UserPlanModel>,
    private val progressList: List<Int>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<BoughtMedicineAdapter.PlanViewHolder>() {
    private val medicineBoughtList =
        plans.filter { it.status.equals("medicine_active", ignoreCase = true) }
    private val imageList = listOf(
        R.drawable.medicinew,
        R.drawable.medicine1,
        R.drawable.medicine3,
        R.drawable.medicine5,

        R.drawable.medicine7,
        R.drawable.medicine8,

        )


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PlanViewHolder {
        val binding = ItemMedicineInvestmentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlanViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: PlanViewHolder,
        position: Int
    ) {
        holder.bind(medicineBoughtList[position],progressList[position])

    }

    override fun getItemCount(): Int = medicineBoughtList.size

    interface OnItemClickListener {
        fun onPlanClick(plan: UserPlanModel)
    }

    inner class PlanViewHolder(val binding: ItemMedicineInvestmentBinding) :
        RecyclerView.ViewHolder(binding.root) {


        fun bind(plan: UserPlanModel, progress:Int) {
            binding.tvName.text = plan.plan_name
            binding.tvPrice.text = "$${"%.1f".format(plan.invested_amount)}"
            binding.tvChange.text = "+${"%.1f".format(plan.profitTrack)}"
            binding.tvSymbol.text = plan.plan_name

            binding.planDays.text = ""
            binding.daysTV.text=""
            // Set the progress bar
            binding.earningProgress.max = 100  // Set max value to 100% progress
            binding.earningProgress.progress = progress  // Set current progress value
            val randomImage = imageList.random()
            binding.imgLogo.setImageResource(randomImage)
            binding.root.setOnClickListener {
                listener.onPlanClick(plan)
            }
        }
    }


}