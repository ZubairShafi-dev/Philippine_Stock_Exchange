package com.pse.pse.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.pse.pse.data.repository.BuyPlanRepo
import com.pse.pse.databinding.BuyPlanSheetBinding
import com.pse.pse.models.Plan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

class BuyPlanSheet(
    private val plan: Plan,
    private val repo: BuyPlanRepo,
    private val uid: String
) : BottomSheetDialogFragment() {

    private var _binding: BuyPlanSheetBinding? = null
    private val binding get() = _binding!!

    private var loadingOverlay: View? = null
    private val df = DecimalFormat("#.##") // format to 2 decimal places

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BuyPlanSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Populate header & chips
        binding.tvPlanName.text = plan.planName
        binding.chipDailyRoi.text = "Daily ROI: ${df.format(plan.dailyPercentage)}%"
        binding.chipTotalPayout.text = "Total Payout: ${df.format(plan.totalPayout ?: 0.0)}%"

        // Limits
        binding.tvMin.text = "$${df.format(plan.minAmount)}"
        binding.tvMax.text = plan.maxAmount?.let { "$${df.format(it)}" } ?: "No limit"

        // Helper text shows min/max live
        binding.tilAmount.helperText = buildString {
            append("Min: $${df.format(plan.minAmount)}")
            plan.maxAmount?.let { append(" • Max: $${df.format(it)}") }
        }

        // Quick fill chips
        binding.chipMin.setOnClickListener { binding.etAmount.setText("${plan.minAmount}") }
        binding.chip2xMin.setOnClickListener { binding.etAmount.setText("${plan.minAmount * 2}") }
        binding.chipMax.setOnClickListener {
            plan.maxAmount?.let { binding.etAmount.setText("$it") }
        }

        // Enable/disable CTA
        binding.btnBuy.isEnabled = false
        binding.etAmount.addTextChangedListener { input ->
            val amt = input?.toString()?.toDoubleOrNull() ?: 0.0
            val isAboveMin = amt >= plan.minAmount
            val isBelowMax = plan.maxAmount?.let { amt <= it } ?: true
            binding.btnBuy.isEnabled = isAboveMin && isBelowMax
            binding.tilAmount.error = when {
                !isAboveMin -> "Minimum is $${df.format(plan.minAmount)}"
                !isBelowMax -> "Maximum is $${df.format(plan.maxAmount!!)}"
                else -> null
            }
        }

        // Buy click (same logic as you had)
        binding.btnBuy.setOnClickListener {
            val amount = binding.etAmount.text?.toString()?.toDoubleOrNull() ?: 0.0
            val aboveMin = amount >= plan.minAmount
            val belowMax = plan.maxAmount?.let { amount <= it } ?: true
            if (!aboveMin || !belowMax) return@setOnClickListener

            showLoading()
            binding.btnBuy.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                val status = withContext(Dispatchers.IO) {
                    repo.buyPlan(uid = uid, pkgId = plan.docId, amount = amount)
                }
                hideLoading()
                binding.btnBuy.isEnabled = true

                when (status) {
                    BuyPlanRepo.Status.SUCCESS -> {
                        showSnack("🎉 Purchase successful!"); dismiss()
                    }

                    BuyPlanRepo.Status.MIN_INVEST_ERROR ->
                        showSnack("Minimum investment is $${df.format(plan.minAmount)}.")

                    BuyPlanRepo.Status.INSUFFICIENT_BALANCE ->
                        showSnack("Insufficient balance. Please top up and try again.")

                    BuyPlanRepo.Status.FAILURE ->
                        showSnack("Purchase failed. Please try again later.")
                }
            }
        }
    }

    private fun showSnack(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG).show() }
    }

    private fun showLoading() {
        loadingOverlay?.apply {
            visibility = View.VISIBLE
            bringToFront()
            elevation = 100f
            requestLayout()
        }
    }

    override fun onStart() {
        super.onStart()
        val bs = dialog?.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        // Transparent bottom sheet background so your gradient + card show nicely
        bs.setBackgroundResource(android.R.color.transparent)

        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bs).apply {
            state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    private fun hideLoading() {
        loadingOverlay?.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
