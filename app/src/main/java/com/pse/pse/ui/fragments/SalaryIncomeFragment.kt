package com.pse.pse.ui.fragments

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.pse.pse.databinding.FragmentSalaryIncomeBinding
import com.pse.pse.ui.viewModels.TeamViewModel
import com.pse.pse.utils.SharedPrefManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

class SalaryIncomeFragment : BaseFragment() {

    private var _binding: FragmentSalaryIncomeBinding? = null
    private val binding get() = _binding!!
    private val vm: TeamViewModel by viewModels()

    private var countdown: CountDownTimer? = null
    private var pollJob: Job? = null

    private val currencyFmt by lazy { NumberFormat.getCurrencyInstance(Locale.US) }
    private val dateFmt by lazy {
        SimpleDateFormat("dd MMM yyyy, HH:mm 'UTC'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalaryIncomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        val userId = SharedPrefManager(requireContext()).getId().toString()

        // Ensure the profile exists, then fetch it for UI
        lifecycleScope.launch {
            try {
                vm.initSalary(userId)
            } catch (_: Exception) {
                // surfaced via LiveData below
            }
        }

        vm.salaryProfile.observe(viewLifecycleOwner) { profile ->
            countdown?.cancel()
            binding.stateWindowOpen.isGone = true
            binding.stateActive.isGone = true
            binding.stateEnded.isGone = true

            if (profile == null) {
                binding.root.post {
                    Snackbar.make(
                        binding.root,
                        "Unable to load salary profile",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                return@observe
            }

            when (profile.status) {
                "window_open" -> {
                    binding.stateWindowOpen.isVisible = true

                    // Window dates
                    binding.tvWindowStart.text =
                        profile.windowStart?.toDate()?.let(dateFmt::format) ?: "--"
                    binding.tvWindowEnd.text =
                        profile.windowEnd?.toDate()?.let(dateFmt::format) ?: "--"

                    // progress 0..30 days
                    val nowMs = System.currentTimeMillis()
                    val start = profile.windowStart?.toDate()?.time ?: nowMs
                    val end = profile.windowEnd?.toDate()?.time ?: nowMs
                    val total = max(1L, (end - start))
                    val done = min(total, max(0L, nowMs - start))
                    val pct = ((done.toDouble() / total.toDouble()) * 100).toInt()
                    binding.progressDays.progress = pct
                    binding.tvDaysPct.text = "$pct%"

                    // countdown every second
                    startCountdown(end)

                    // Live preview (we only show snapshot field here; real lock happens at D+30)
                    binding.tvPreviewAdb.text = currencyFmt.format(profile.snapshotDirectBusiness)
                    binding.tvPreviewTier.text = "Tier not locked yet"

                    // Poll every 30s so screen auto-switches when window locks
                    startPolling(userId)
                }

                "active" -> {
                    binding.stateActive.isVisible = true
                    binding.tvLockedAdb.text = currencyFmt.format(profile.snapshotDirectBusiness)
                    binding.tvSalary.text = currencyFmt.format(profile.salaryAmount)
                    binding.tvTier.text = "Tier ${profile.tier}"

                    binding.tvNextPayout.text =
                        profile.nextPayoutAt?.toDate()?.let(dateFmt::format) ?: "--"
                    binding.tvLastPayout.text =
                        profile.lastPayoutAt?.toDate()?.let(dateFmt::format) ?: "--"
                }

                "ended" -> {
                    binding.stateEnded.isVisible = true
                    binding.tvEndedReason.text = profile.reason ?: "Not eligible"
                }
            }
        }
    }

    private fun startCountdown(endMs: Long) {
        val remaining = max(0L, endMs - System.currentTimeMillis())
        countdown = object : CountDownTimer(remaining, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvCountdown.text = formatDuration(millisUntilFinished)
            }

            override fun onFinish() {
                binding.tvCountdown.text = "00:00:00"
                Toast.makeText(
                    requireContext(),
                    "30-day window ended. Locking soon…",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    private fun startPolling(userId: String) {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (true) {
                delay(30_000L) // 30s
                vm.refreshSalary(userId)
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        var s = ms / 1000
        val h = s / 3600; s %= 3600
        val m = s / 60; s %= 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countdown?.cancel()
        pollJob?.cancel()
        _binding = null
    }
}