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
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemUserPlanCardBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: UserPlanUi) {
            val up = item.userPlan

            // Header
            b.tvPlanName.text = item.planName.ifBlank { "Plan" }
            b.tvStatus.text = if (item.isActive) "ACTIVE" else "EXPIRED"

            // Numbers
            b.tvPrincipal.text = "$${trim(up.principal)}"
            b.tvRoi.text = "${trim(up.roiPercent)}% | $${trim(up.roiAmount)}"

            // Footer left: Total Payout
            b.tvTotalPayout.text = "Total Payout: $${trim(up.totalPayoutAmount)}"

            // Progress
            val pct = (item.progress * 100.0).toInt()
            b.tvProgressPct.text = "$pct% completed"
            b.progress.setProgressCompat(pct, true)

            // Dates
            b.tvBuyDate.text = "Bought — " + (up.buyDate?.toDate()?.let(df::format) ?: "—")
            b.tvLastCollect.text = "Last ROI — " + (up.lastCollectedDate?.toDate()?.let(df::format) ?: "—")

            // Referral badge → show only when we have a percent
            val direct = item.directPercent
            if (direct != null) {
                b.tvDirectBadge.visibility = View.VISIBLE
                b.tvDirectBadge.text = "Referral Bonus: ${trim(direct)}%"
            } else {
                b.tvDirectBadge.visibility = View.GONE
            }

            b.root.setOnClickListener { onClick(item) }
        }

        private fun trim(v: Double): String {
            return if (v % 1.0 == 0.0) v.toInt().toString()
            else String.format(Locale.US, "%.2f", v)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inf = LayoutInflater.from(parent.context)
        val binding = ItemUserPlanCardBinding.inflate(inf, parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size
}