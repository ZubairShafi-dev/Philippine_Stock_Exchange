package com.pse.pse.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.lifecycle.lifecycleScope
import com.pse.pse.R
import com.pse.pse.databinding.FragmentTeamRankingBinding
import com.pse.pse.models.AchievementModel
import com.pse.pse.models.LevelCondition
import com.pse.pse.models.TeamStats
import com.pse.pse.ui.viewModels.TeamViewModel
import com.pse.pse.utils.SharedPrefManager
import kotlinx.coroutines.launch

class TeamRankingFragment : BaseFragment() {

    private var _binding: FragmentTeamRankingBinding? = null
    private val binding get() = _binding
    private val teamViewModel: TeamViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTeamRankingBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        val userId = SharedPrefManager(requireContext()).getId()

        teamViewModel.fetchTeamStats(userId.toString())
        Log.d("TeamRankingFragment", "Fetching stats for user: $userId")

        val levelTitles = mapOf(
            1 to "Rising Star",
            2 to "Elite Trailblazer",
            3 to "Visionary Leader",
            4 to "Platinum Vanguard ",
            5 to "Diamond Pioneer",
            6 to "Ruby Champion",
            7 to "Emerald Titan",
            8 to "Sapphire Monarch",
            9 to "Titanium Overlord",
            10 to "Supreme Emperor"
        )

        val levelLayouts = mapOf(
            1 to binding?.starterSquadLayout,
            2 to binding?.growingGangLayout,
            3 to binding?.solidCircleLayout,
            4 to binding?.powerPackLayout,
            5 to binding?.eliteCrewLayout,
            6 to binding?.ultimateForceLayout,
            7 to binding?.emeraldTitan,
            8 to binding?.saphireMonarchLayout,
            9 to binding?.titaniumOverLoadLAyout,
            10 to binding?.supremeEmperorLayout
        )

        val lockIcons = mapOf(
            1 to binding?.starterSquadLockedIcon,
            2 to binding?.growingGangLockedIcon,
            3 to binding?.solidCircleLockedIcon,
            4 to binding?.powerPackLockedIcon,
            5 to binding?.eliteCrewLockedIcon,
            6 to binding?.ultimateForceLockedIcon,
            7 to binding?.emeraldTitanLockedIcon,
            8 to binding?.saphireMonarchLockIcon,
            9 to binding?.titaniumOverLoadLockIcon,
            10 to binding?.supremeEmperorLock
        )

        teamViewModel.teamStats.observe(viewLifecycleOwner) { stats ->
            Log.d("TeamRankingFragment", "Stats updated: $stats")
            lockIcons.forEach { (level, icon) ->
                if (stats.unlockedLevels.contains(level)) {
                    icon?.setImageResource(R.drawable.ic_star)
                } else {
                    icon?.setImageResource(R.drawable.ic_lock)
                }
            }

            levelLayouts.forEach { (level, layout) ->
                layout?.setOnClickListener {
                    val isUnlocked = stats.unlockedLevels.contains(level)
                    showTeamDialog(levelTitles[level] ?: "", level, stats, isUnlocked)
                }
            }
        }

        view.findViewById<View>(R.id.menuIcon)?.setOnClickListener {
            val drawerLayout = activity?.findViewById<DrawerLayout>(R.id.drawerLayout)
            drawerLayout?.openDrawer(GravityCompat.START)
        }
    }

    private fun showTeamDialog(
        levelName: String,
        level: Int,
        stats: TeamStats,
        isUnlocked: Boolean
    ) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialoge_team_reward_requirements)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(true)

        val tvTitle = dialog.findViewById<TextView>(R.id.teamRankingTitle)
        val collectButton = dialog.findViewById<Button>(R.id.collectRewardButton)
        val minInvestmentAmount = dialog.findViewById<TextView>(R.id.minInvestmentAmount)
        val activeTeamAmount = dialog.findViewById<TextView>(R.id.earningAmount)
        val directBusinessAmount = dialog.findViewById<TextView>(R.id.currentDirectBuisnssAmount)
        val groupSellAmount = dialog.findViewById<TextView>(R.id.groupSellAmount)

        val minInvestmentIndicator =
            dialog.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(
                R.id.minInvestmentIndicator
            )
        val teamIndicator =
            dialog.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(
                R.id.teamIndicator
            )
        val directBusinessIndicator =
            dialog.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(
                R.id.directBusinessIndicator
            )
        val groupSellIndicator =
            dialog.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(
                R.id.groupSellIndicator
            )

        tvTitle?.text = levelName

        val requirement = when (level) {
            1 -> LevelCondition(1, 50.0, 5, 500.0, 2500.0)
            2 -> LevelCondition(2, 100.0, 10, 1500.0, 9000.0)
            3 -> LevelCondition(3, 150.0, 15, 2500.0, 25000.0)
            4 -> LevelCondition(4, 500.0, 20, 6500.0, 50000.0)
            5 -> LevelCondition(5, 1000.0, 35, 12000.0, 100000.0)
            6 -> LevelCondition(6, 2000.0, 50, 20000.0, 200000.0)
            7 -> LevelCondition(7, 3000.0, 75, 35000.0, 350000.0)
            8 -> LevelCondition(8, 5000.0, 100, 50000.0, 550000.0)
            9 -> LevelCondition(9, 8000.0, 150, 75000.0, 1000000.0)
            10 -> LevelCondition(10, 12000.0, 200, 125000.0, 3000000.0)
            else -> null
        }

        requirement?.let { req ->
            minInvestmentAmount?.text = "$${stats.currentInvestment}/${req.minInvestment}"
            activeTeamAmount?.text = "${stats.activeMembers}/${req.activeMembers}"
            directBusinessAmount?.text = "$${stats.directBusiness}/${req.directBusiness}"
            groupSellAmount?.text = "$${stats.groupSell}/${req.groupSell}"

            minInvestmentIndicator?.max = req.minInvestment.toInt()
            minInvestmentIndicator?.progress =
                stats.currentInvestment.toInt().coerceAtMost(minInvestmentIndicator.max)

            teamIndicator?.max = req.activeMembers
            teamIndicator?.progress = stats.activeMembers.coerceAtMost(teamIndicator.max)

            directBusinessIndicator?.max = req.directBusiness.toInt()
            directBusinessIndicator?.progress =
                stats.directBusiness.toInt().coerceAtMost(directBusinessIndicator.max)

            groupSellIndicator?.max = req.groupSell.toInt()
            groupSellIndicator?.progress =
                stats.groupSell.toInt().coerceAtMost(groupSellIndicator.max)
        }

        collectButton?.setOnClickListener {
            dialog.dismiss()

            val requirementMet = requirement?.let { req ->
                stats.currentInvestment >= req.minInvestment &&
                        stats.activeMembers >= req.activeMembers &&
                        stats.directBusiness >= req.directBusiness &&
                        stats.groupSell >= req.groupSell
            } == true

            val rewardAmount = getRewardForLevel(level)
            showCollectionDialogue(requirementMet, level, levelName, rewardAmount)
        }

        dialog.show()
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showCollectionDialogue(
        status: Boolean,
        level: Int,
        levelName: String,
        rewardAmount: Double
    ) {
        val collectionDialog = Dialog(requireContext())
        collectionDialog.setContentView(
            if (status) R.layout.dialoge_collect_reward else R.layout.dialoge_collect_reward_locked
        )
        collectionDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        collectionDialog.setCanceledOnTouchOutside(true)
        collectionDialog.findViewById<TextView>(R.id.tvRewardAmount)?.text = "$$rewardAmount"
        collectionDialog.findViewById<TextView>(R.id.collectRewardLocked)?.text = "$$rewardAmount"
        if (status) {
            val button = collectionDialog.findViewById<Button>(R.id.collectRewardButton)
            val alreadyClaimed = teamViewModel.claimedRewards.contains(level)

            if (alreadyClaimed) {
                button.text = "Reward Claimed"
                button.isEnabled = false
                button.alpha = 0.5f
            } else {
                button.setOnClickListener {
                    collectionDialog.dismiss()
                    showLoading()
                    val userId = SharedPrefManager(requireContext()).getId().toString()
                    val model = AchievementModel(
                        rankName = levelName,
                        userId = userId,
                        rewardAmount = rewardAmount
                    )

                    lifecycleScope.launch {
                        try {
                            teamViewModel.collectReward(model)
                            teamViewModel.rewardResult.observe(viewLifecycleOwner) { success ->
                                if (success) {
                                    Log.d(
                                        "TeamRankingFragment",
                                        "Reward for $levelName claimed successfully"
                                    )
                                    collectionDialog.dismiss()
                                    showRewardSuccessDialog()
                                } else {
                                    Toast.makeText(
                                        requireContext(),
                                        "Failed to claim reward!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                hideLoading()
                            }
                        } catch (e: Exception) {
                            hideLoading()
                            Toast.makeText(
                                requireContext(),
                                "Unexpected error: ${e.localizedMessage}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        collectionDialog.show()
    }

    private fun showRewardSuccessDialog() {
        val successDialog = Dialog(requireContext())
        successDialog.setContentView(R.layout.dialoge_reward_collected_success)
        successDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        successDialog.setCanceledOnTouchOutside(true)
        successDialog.show()

        Handler(Looper.getMainLooper()).postDelayed({
            if (successDialog.isShowing) successDialog.dismiss()
        }, 3000)
    }

    private fun getRewardForLevel(level: Int): Double {
        return when (level) {
            1 -> 50.0
            2 -> 200.0
            3 -> 650.0
            4 -> 1200.0
            5 -> 2050.0
            6 -> 3200.0
            7 -> 4800.0
            8 -> 7800.0
            9 -> 15000.0
            10 -> 30000.0
            else -> 0.0
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}