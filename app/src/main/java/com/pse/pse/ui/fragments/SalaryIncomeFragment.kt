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
import com.pse.pse.R
import com.pse.pse.databinding.FragmentSalaryIncomeBinding
import com.pse.pse.viewModels.TeamViewModel
import com.pse.pse.utils.SharedPrefManager
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
    private var liveAdbReqId = 0


    private val currencyFmt by lazy { NumberFormat.getCurrencyInstance(Locale.US) }
    private val dateFmt by lazy {
        SimpleDateFormat("dd MMM yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC") // keep times in UTC, but we don't print "UTC"
        }
    }

    private var gateProfileReady = false
    private var gateAdbReady = true // true by default; set to false only while fetching live ADB

    private fun maybeHideLoading() {
        if (gateProfileReady && gateAdbReady) hideLoading()
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

        // reset gating each time the view is created
        gateProfileReady = false
        gateAdbReady = true
        liveAdbReqId = 0

        val userId = SharedPrefManager(requireContext()).getId()
        if (userId.isNullOrBlank()) {
            showError("Unable to load salary profile: user not logged in.")
            return
        }

        showLoading()
        vm.observeSalary(userId)

        vm.salaryProfile.observe(viewLifecycleOwner) { profile ->
            if (profile == null) {
                Snackbar.make(binding.root, "Unable to load salary profile", Snackbar.LENGTH_LONG)
                    .show()
                // We won't keep the spinner forever if something's wrong:
                hideLoading()
                return@observe
            }

            countdown?.cancel()
            binding.stateWindowOpen.isGone = true
            binding.stateActive.isGone = true
            binding.stateEnded.isGone = true

            when (profile.status) {
                "window_open" -> {
                    binding.stateWindowOpen.isVisible = true

                    // progress 0..30 days (unchanged) ...
                    val nowMs = System.currentTimeMillis()
                    val start = profile.windowStart?.toDate()?.time ?: nowMs
                    val end = profile.windowEnd?.toDate()?.time ?: nowMs
                    val total = max(1L, (end - start))
                    val done = min(total, max(0L, nowMs - start))
                    val pct = ((done.toDouble() / total.toDouble()) * 100).toInt()
                    binding.progressDays.progress = pct
                    binding.tvDaysPct.text = "$pct%"

                    binding.tvWindowStart.text =
                        profile.windowStart?.toDate()?.let(dateFmt::format) ?: "--"
                    binding.tvWindowEnd.text =
                        profile.windowEnd?.toDate()?.let(dateFmt::format) ?: "--"

                    // Placeholder while we fetch live ADB
                    val snapshotText = currencyFmt.format(profile.snapshotDirectBusiness)
                    binding.tvPreviewAdb.text = getString(R.string.calculating_ellipsis)
                    binding.tvPreviewAdb.alpha = 0.7f
                    binding.tvPreviewTier.text = "Tier not locked yet"

                    startCountdown(end)

                    // GATE: profile is ready; live ADB still loading
                    gateProfileReady = true
                    gateAdbReady = false

                    val myReq = ++liveAdbReqId
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val liveAdb = vm.fetchSalaryCurrentAdb(userId)
                            if (myReq != liveAdbReqId) return@launch
                            val displayText = if (liveAdb != null) {
                                currencyFmt.format(liveAdb)
                            } else {
                                currencyFmt.format(profile.snapshotDirectBusiness)
                            }
                            binding.tvPreviewAdb.text = displayText
                        } catch (_: Exception) {
                            if (myReq == liveAdbReqId) {
                                binding.tvPreviewAdb.text =
                                    currencyFmt.format(profile.snapshotDirectBusiness)
                            }
                        } finally {
                            if (myReq == liveAdbReqId) {
                                binding.tvPreviewAdb.alpha = 1f
                                gateAdbReady = true
                                maybeHideLoading()
                            }
                        }
                    }
                }

                "active" -> {
                    liveAdbReqId++
                    binding.stateActive.isVisible = true
                    binding.tvLockedAdb.text = currencyFmt.format(profile.snapshotDirectBusiness)
                    binding.tvSalary.text = currencyFmt.format(profile.salaryAmount)
                    binding.tvTier.text = "Tier ${profile.tier}"

                    binding.tvNextPayout.text =
                        profile.nextPayoutAt?.toDate()?.let(dateFmt::format) ?: "--"
                    binding.tvLastPayout.text =
                        profile.lastPayoutAt?.toDate()?.let(dateFmt::format) ?: "--"

                    // GATE: no live ADB call in this state
                    gateProfileReady = true
                    gateAdbReady = true
                    maybeHideLoading()
                }

                "ended" -> {
                    liveAdbReqId++
                    binding.stateEnded.isVisible = true
                    binding.tvEndedReason.text = profile.reason ?: "Not eligible"

                    // GATE: no live ADB call in this state
                    gateProfileReady = true
                    gateAdbReady = true
                    maybeHideLoading()
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

    private fun formatDuration(ms: Long): String {
        var s = ms / 1000
        val h = s / 3600; s %= 3600
        val m = s / 60; s %= 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countdown?.cancel()
        _binding = null
    }
}