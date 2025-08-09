// app/src/main/java/com/pse/pse/adapters/LeadershipTierAdapter.kt
package com.pse.pse.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pse.pse.R
import com.pse.pse.models.LeadershipProgress
import com.pse.pse.models.LeadershipTiers
import java.text.NumberFormat
import java.util.Locale

class LeadershipTierAdapter : RecyclerView.Adapter<LeadershipTierAdapter.VH>() {
    private var awarded: Set<Int> = emptySet()
    private var activeCount: Int = 0
    private val cur = NumberFormat.getCurrencyInstance(Locale.US)

    fun submit(progress: LeadershipProgress) {
        awarded = progress.awarded.toSet()
        activeCount = progress.directActiveCount
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leadership_tier, parent, false)
        return VH(v)
    }

    override fun getItemCount() = LeadershipTiers.TIERS.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tier = LeadershipTiers.TIERS[position]
        holder.tvMembers.text = "%,d members".format(tier.members)
        holder.tvBonus.text = "${cur.format(tier.bonus)}"
        val reached = activeCount >= tier.members
        val paid = awarded.contains(tier.members)

        holder.ivStatus.setImageResource(
            when {
                paid -> R.drawable.ic_check_circle_green // green
                reached -> R.drawable.ic_progress_dot // reached but not yet paid (rare due to auto award)
                else -> R.drawable.ic_radio_off
            }
        )
        holder.itemView.alpha = if (paid) 1f else if (reached) 0.95f else 0.7f
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivStatus: ImageView = v.findViewById(R.id.ivStatus)
        val tvMembers: TextView = v.findViewById(R.id.tvMembers)
        val tvBonus: TextView = v.findViewById(R.id.tvBonus)
    }
}
