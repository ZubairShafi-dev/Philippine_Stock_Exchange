// app/src/main/java/com/pse/pse/ui/fragments/LeadershipFragment.kt
package com.pse.pse.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pse.pse.adapters.LeadershipTierAdapter
import com.pse.pse.databinding.FragmentLeadershipBinding
import com.pse.pse.models.LeadershipTiers
import com.pse.pse.viewModels.LeadershipViewModel
import com.pse.pse.utils.SharedPrefManager
import kotlinx.coroutines.flow.collectLatest
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class LeadershipFragment : BaseFragment() {
    private var _binding: FragmentLeadershipBinding? = null
    private val binding get() = _binding!!
    private val vm: LeadershipViewModel by viewModels()
    private val adapter = LeadershipTierAdapter()
    private val fmt = NumberFormat.getIntegerInstance(Locale.US)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeadershipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tiersRv.layoutManager = LinearLayoutManager(requireContext())
        binding.tiersRv.adapter = adapter

        val uid = SharedPrefManager(requireContext()).getId().orEmpty()
        vm.start(uid) // realtime + call ensureCheckNow()

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.progress.collectLatest { p ->
                // Count + next milestone
                binding.tvActiveDirects.text = fmt.format(p.directActiveCount)

                val tiers = LeadershipTiers.TIERS
                val next = tiers.firstOrNull { p.directActiveCount < it.members }
                val maxTier = tiers.last()
                val target = next?.members ?: maxTier.members

                binding.tvNextTarget.text =
                    if (next == null) "Max milestone reached" else "Next target: %,d".format(target)

                // progress bar within current step (e.g., 100→500)
                val lower = tiers.lastOrNull { it.members <= p.directActiveCount }?.members ?: 0
                val upper = target
                val range = max(1, upper - lower)
                val done = min(range, max(0, p.directActiveCount - lower))
                val pct = (done.toDouble() / range * 100).toInt()

                binding.stepProgress.setProgressCompat(pct, true)
                binding.tvStepPct.text = "$pct%"

                binding.awardBadge.isVisible = (p.lastAwardAmount ?: 0.0) > 0.0
                binding.tvLastAward.text = if ((p.lastAwardAmount ?: 0.0) > 0.0)
                    "Last award: $${"%,.2f".format(p.lastAwardAmount)}"
                else "Last award: $0.0"

                adapter.submit(p)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}