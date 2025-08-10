package com.pse.pse.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
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
            b.tvPlanName.text = item.planName.ifBlank { "Plan" }
            b.tvStatus.text = if (item.isActive) "ACTIVE" else "EXPIRED"
            b.tvPrincipal.text = "$${trim(up.principal)}"
            b.tvRoi.text = "${trim(up.roiPercent)}% | $${trim(up.roiAmount)}"
            b.tvTotalPayout.text = "$${trim(up.totalPayoutAmount)}"

            val pct = (item.progress * 100.0).toInt()
            b.tvProgressPct.text = "$pct% completed"
            (b.progress as LinearProgressIndicator).setProgressCompat(pct, true)

            b.tvBuyDate.text = "Bought — " + (up.buyDate?.toDate()?.let(df::format) ?: "—")
            b.tvLastCollect.text =
                "Last ROI — " + (up.lastCollectedDate?.toDate()?.let(df::format) ?: "—")

            b.tvDirectBadge.text = item.directPercent?.let { "Direct: ${trim(it)}%" } ?: ""

            b.root.setOnClickListener { onClick(item) }
        }

        private fun trim(v: Double): String {
            return if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.2f", v)
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