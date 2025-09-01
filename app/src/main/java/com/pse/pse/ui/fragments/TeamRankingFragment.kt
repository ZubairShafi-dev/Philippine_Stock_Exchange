package com.pse.pse.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.viewModels
import com.pse.pse.R
import com.pse.pse.databinding.FragmentTeamRankingBinding
import com.pse.pse.models.LevelCondition
import com.pse.pse.models.TeamStats
import com.pse.pse.viewModels.TeamViewModel
import com.pse.pse.utils.SharedPrefManager

class TeamRankingFragment : BaseFragment() {

    private var _binding: FragmentTeamRankingBinding? = null
    private val binding get() = _binding!!
    private val vm: TeamViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentTeamRankingBinding.inflate(inflater, container, false)
        .also { _binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        val userId = SharedPrefManager(requireContext()).getId().toString()
        vm.fetchTeamStats(userId)

        /* rank names */
        val levelTitles = mapOf(
            1 to "Astro Cadet",
            2 to "Star Commander",
            3 to "Galaxy Leader",
            4 to "Nova Captain",
            5 to "Solar General"
        )

        /* clickable card roots (only first five kept) */
        val levelLayouts = mapOf(
            1 to binding.starterSquadLayout,
            2 to binding.growingGangLayout,
            3 to binding.solidCircleLayout,
            4 to binding.powerPackLayout,
            5 to binding.eliteCrewLayout
        )

        /* lock / star icons for same five */
        val lockIcons = mapOf(
            1 to binding.starterSquadLockedIcon,
            2 to binding.growingGangLockedIcon,
            3 to binding.solidCircleLockedIcon,
            4 to binding.powerPackLockedIcon,
            5 to binding.eliteCrewLockedIcon
        )

        vm.teamStats.observe(viewLifecycleOwner) { stats ->
            Log.d("TeamRankingFragment", "stats: $stats")

            /* update icons */
            lockIcons.forEach { (lvl, icon) ->
                icon.setImageResource(
                    if (stats.unlockedLevels.contains(lvl))
                        R.drawable.ic_star else R.drawable.ic_lock
                )
            }

            /* click listeners */
            levelLayouts.forEach { (lvl, layout) ->
                layout.setOnClickListener {
                    val unlocked = stats.unlockedLevels.contains(lvl)
                    showRankDialog(levelTitles[lvl] ?: "", lvl, stats, unlocked)
                }
            }
        }

        view.findViewById<View>(R.id.menuIcon).setOnClickListener {
            activity?.findViewById<DrawerLayout>(R.id.drawerLayout)
                ?.openDrawer(GravityCompat.START)
        }
    }

    /* ───────── dialog ───────── */
    private fun showRankDialog(
        rankName: String,
        level: Int,
        stats: TeamStats,
        isUnlocked: Boolean
    ) {
        val dlg = Dialog(requireContext()).apply {
            setContentView(R.layout.dialoge_team_reward_requirements)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setCanceledOnTouchOutside(true)
        }

        dlg.findViewById<TextView>(R.id.teamRankingTitle).text = rankName
        val collectBtn = dlg.findViewById<Button>(R.id.collectRewardButton)

        /* only self-deposit matters */
        val requirement = when (level) {
            1 -> LevelCondition(1, 2500.0)
            2 -> LevelCondition(2, 5000.0)
            3 -> LevelCondition(3, 15000.0)
            4 -> LevelCondition(4, 50000.0)
            5 -> LevelCondition(5, 100000.0)
            else -> null
        }

        requirement?.let { req ->
            /* update the one visible line + progress bar */
            dlg.findViewById<TextView>(R.id.minInvestmentAmount)
                ?.text = "$${stats.currentInvestment}/${req.minInvestment}"

            val bar = dlg.findViewById<
                    com.google.android.material.progressindicator.LinearProgressIndicator
                    >(R.id.minInvestmentIndicator)

            bar?.max = req.minInvestment.toInt()
            bar?.progress = stats.currentInvestment
                .toInt().coerceAtMost(bar.max)
        }

        /* collect button */
        collectBtn.setOnClickListener {
            dlg.dismiss()
            Toast.makeText(requireContext(), "Coming Soon", Toast.LENGTH_SHORT).show()
        }

        dlg.show()
        dlg.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}