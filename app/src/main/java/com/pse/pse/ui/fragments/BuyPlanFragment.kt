package com.pse.pse.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.pse.pse.data.repository.BuyPlanRepo
import com.pse.pse.databinding.FragmentBuyPlanBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

class BuyPlanFragment : BaseFragment() {

    private var _binding: FragmentBuyPlanBinding? = null
    private val binding get() = _binding!!

    private val df = DecimalFormat("#.##") // 2 decimals

    // Args (kept simple to avoid model changes)
    private val planName by lazy { requireArguments().getString(ARG_PLAN_NAME).orEmpty() }
    private val minAmount by lazy { requireArguments().getDouble(ARG_MIN_AMOUNT) }
    private val maxAmountRaw by lazy { requireArguments().getDouble(ARG_MAX_AMOUNT) } // -1.0 = no limit
    private val hasMax by lazy { maxAmountRaw >= 0.0 }
    private val maxAmount by lazy { maxAmountRaw.takeIf { it >= 0.0 } }
    private val dailyPercent by lazy { requireArguments().getDouble(ARG_DAILY_PERCENT) }
    private val totalPayout by lazy { requireArguments().getDouble(ARG_TOTAL_PAYOUT) }
    private val docId by lazy { requireArguments().getString(ARG_DOC_ID).orEmpty() }
    private val uid by lazy { requireArguments().getString(ARG_UID).orEmpty() }

    private val repo by lazy { BuyPlanRepo() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBuyPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Drawer trigger works if the layout has menuIcon
        setupDrawerTrigger(binding.root)

        // Header
        binding.tvPlanName.text = planName
        binding.chipDailyRoi.text = "Daily ROI: ${df.format(dailyPercent)}%"
        binding.chipTotalPayout.text = "Total Payout: ${df.format(totalPayout)}%"

        // Limits
        binding.tvMin.text = "$${df.format(minAmount)}"
        binding.tvMax.text = if (hasMax) "$${df.format(maxAmount!!)}" else "No limit"

        // Helper text: min/max
        binding.tilAmount.helperText = buildString {
            append("Min: $${df.format(minAmount)}")
            if (hasMax) append(" • Max: $${df.format(maxAmount!!)}")
        }

        // Quick fill chips
        binding.chipMin.setOnClickListener { binding.etAmount.setText("$minAmount") }
        binding.chip2xMin.setOnClickListener { binding.etAmount.setText("${minAmount * 2}") }
        binding.chipMax.apply {
            visibility = if (hasMax) View.VISIBLE else View.GONE
            setOnClickListener { maxAmount?.let { binding.etAmount.setText("$it") } }
        }

        // CTA enablement
        binding.btnBuy.isEnabled = false
        binding.etAmount.addTextChangedListener { input ->
            val amt = input?.toString()?.toDoubleOrNull() ?: 0.0
            val isAboveMin = amt >= minAmount
            val isBelowMax = if (hasMax) amt <= (maxAmount ?: Double.MAX_VALUE) else true

            binding.btnBuy.isEnabled = isAboveMin && isBelowMax
            binding.tilAmount.error = when {
                !isAboveMin -> "Minimum is $${df.format(minAmount)}"
                !isBelowMax -> "Maximum is $${df.format(maxAmount ?: 0.0)}"
                else -> null
            }
        }

        // Buy click → SAME repo + result contract as before
        binding.btnBuy.setOnClickListener {
            val amount = binding.etAmount.text?.toString()?.toDoubleOrNull() ?: 0.0
            val aboveMin = amount >= minAmount
            val belowMax = if (hasMax) amount <= (maxAmount ?: Double.MAX_VALUE) else true
            if (!aboveMin || !belowMax) return@setOnClickListener

            showLoading()
            binding.btnBuy.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                val status = withContext(Dispatchers.IO) {
                    repo.buyPlan(uid = uid, pkgId = docId, amount = amount)
                }

                hideLoading()
                binding.btnBuy.isEnabled = true

                when (status) {
                    BuyPlanRepo.Status.SUCCESS -> {
                        // Send result exactly like sheet did, then go back
                        parentFragmentManager.setFragmentResult(
                            PlanFragment.BUY_RESULT_KEY,
                            bundleOf(PlanFragment.BUY_RESULT_SUCCESS to true)
                        )
                        findNavController().popBackStack()
                    }

                    BuyPlanRepo.Status.MIN_INVEST_ERROR ->
                        showSnack("Minimum investment is $${df.format(minAmount)}.")

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        // Simple arg keys; no model changes, no Parcelize required
        const val ARG_PLAN_NAME = "arg_plan_name"
        const val ARG_MIN_AMOUNT = "arg_min_amount"
        const val ARG_MAX_AMOUNT = "arg_max_amount" // -1.0 → no limit
        const val ARG_DAILY_PERCENT = "arg_daily_percent"
        const val ARG_TOTAL_PAYOUT = "arg_total_payout"
        const val ARG_DOC_ID = "arg_doc_id"
        const val ARG_UID = "arg_uid"
    }
}