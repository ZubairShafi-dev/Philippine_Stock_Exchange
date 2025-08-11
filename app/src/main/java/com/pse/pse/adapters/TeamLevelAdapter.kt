package com.pse.pse.adapters

import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.pse.pse.R
import com.pse.pse.models.TeamLevelModel
import com.pse.pse.models.TeamLevelStatus

/**
 * Shows each team level card and lets the caller react when an **unlocked**
 * level is tapped.  The click-callback now passes the *whole* [TeamLevelModel]
 * so the next screen can access the embedded user list.
 */
class TeamLevelAdapter(
    private val onLevelClick: (TeamLevelModel) -> Unit
) : ListAdapter<TeamLevelStatus, TeamLevelAdapter.TeamVH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_team_levels, parent, false)
        return TeamVH(view)
    }

    override fun onBindViewHolder(holder: TeamVH, position: Int) =
        holder.bind(getItem(position))

    /* ────────────────────────────── ViewHolder ───────────────────────── */
    inner class TeamVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val card: MaterialCardView = itemView.findViewById(R.id.levelCard)
        private val levelTxt: TextView = itemView.findViewById(R.id.levelNumber)
        private val required: TextView = itemView.findViewById(R.id.requiredUser)
        private val profitPct: TextView = itemView.findViewById(R.id.levelProfit)
        private val totalUser: TextView = itemView.findViewById(R.id.totalUsers)
        private val active: TextView = itemView.findViewById(R.id.activeUsers)
        private val inactive: TextView = itemView.findViewById(R.id.inactiveUsers)
        private val deposit: TextView = itemView.findViewById(R.id.totalDeposit)
        private val lockImg: ImageView = itemView.findViewById(R.id.lockOverlay)

        init {
            card.setOnClickListener {
                val pos = adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val status = getItem(pos)
                if (status.levelUnlocked) {
                    onLevelClick(status.teamLevel)   // hand off full model
                }
            }
        }

        fun bind(status: TeamLevelStatus) = with(status.teamLevel) {
            // Header
            levelTxt.text = "Level $level"

            // ✅ Values ONLY (no labels, no "\n")
            required.text   = requiredMembers.toString()
            profitPct.text  = "${profitPercentage.toInt()}%"
            totalUser.text  = totalUsers.toString()
            active.text     = activeUsers.toString()
            inactive.text   = inactiveUsers.toString()
            deposit.text    = "$" + String.format("%,.0f", totalDeposit) // e.g., $0

            // Lock visuals
            if (status.levelUnlocked) {
                lockImg.visibility = View.GONE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) card.setRenderEffect(null)
                card.foreground = null
            } else {
                lockImg.visibility = View.VISIBLE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    card.setRenderEffect(RenderEffect.createBlurEffect(4f, 4f, Shader.TileMode.CLAMP))
                }
                card.foreground = ColorDrawable(Color.argb(64, 0, 0, 0))
            }

            lockImg.setColorFilter(ContextCompat.getColor(itemView.context, R.color.Light_Grey))
        }
    }

    /* ────────────────────────────── DiffUtil ─────────────────────────── */
    private class Diff : DiffUtil.ItemCallback<TeamLevelStatus>() {
        override fun areItemsTheSame(
            oldItem: TeamLevelStatus,
            newItem: TeamLevelStatus
        ): Boolean = oldItem.teamLevel.level == newItem.teamLevel.level

        override fun areContentsTheSame(
            oldItem: TeamLevelStatus,
            newItem: TeamLevelStatus
        ): Boolean = oldItem == newItem
    }
}