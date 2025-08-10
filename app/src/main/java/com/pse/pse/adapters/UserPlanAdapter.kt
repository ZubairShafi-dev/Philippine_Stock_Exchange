package com.pse.pse.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pse.pse.databinding.ItemUserPlanCardBinding
import com.pse.pse.models.UserPlanUi
import java.text.SimpleDateFormat
import java.util.Locale

class UserPlanAdapter(
    private val onClick: (UserPlanUi) -> Unit = {}
) : RecyclerView.Adapter<UserPlanAdapter.VH>() {

    private val items = mutableListOf<UserPlanUi>()
    private val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    fun submit(list: List<UserPlanUi>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    inner class VH(val b: ItemUserPlanCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: UserPlanUi) {
            val up = item.userPlan

            // Title + status
            b.tvPlanName.text = item.planName.ifBlank { "Plan" }
            b.tvStatus.text = if (item.isActive) "ACTIVE" else "EXPIRED"

            // KPIs
            b.tvPrincipal.text = "$${trim(up.principal)}"
            b.tvRoi.text = "${trim(up.roiPercent)}% | $${trim(up.roiAmount)}"

            // Progress
            val pct = (item.progress * 100.0).toInt()
            b.tvProgressPct.text = "$pct% completed"
            b.progress.setProgressCompat(pct, true)

            // Meta
            b.tvBought.text = "Bought — " + (up.buyDate?.toDate()?.let(df::format) ?: "—")
            b.tvLastRoi.text =
                "Last ROI — " + (up.lastCollectedDate?.toDate()?.let(df::format) ?: "—")

            // Bottom row
            b.tvTotalPayout.text = "Total Payout: $${trim(up.totalPayoutAmount)}"
            item.directPercent?.let {
                b.tvDirectBadge.visibility = View.VISIBLE
                b.tvDirectBadge.text = "Referral Bonus: ${trim(it)}%"
            } ?: run { b.tvDirectBadge.visibility = View.GONE }

            b.root.setOnClickListener { onClick(item) }
        }

        private fun trim(v: Double): String =
            if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.2f", v)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding =
            ItemUserPlanCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size
}