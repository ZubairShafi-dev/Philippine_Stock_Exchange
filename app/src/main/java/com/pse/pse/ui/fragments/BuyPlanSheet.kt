package com.pse.pse.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.pse.pse.R
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

        // ---------- Setup Loading Overlay ----------
        var container: ViewGroup = binding.root
        if (binding.root is ScrollView && binding.root.childCount == 1) {
            val originalChild = binding.root.getChildAt(0)
            (binding.root as ScrollView).removeView(originalChild)
            val frameLayout = FrameLayout(requireContext())
            frameLayout.layoutParams = originalChild.layoutParams
            frameLayout.addView(originalChild)
            (binding.root as ScrollView).addView(frameLayout)
            container = frameLayout
        }
        loadingOverlay = LayoutInflater.from(context)
            .inflate(R.layout.loading_overlay, container, false)
        container.addView(loadingOverlay)
        hideLoading()

        // ---------- Set plan details ----------
        binding.tvPlanName.text = plan.planName
        binding.tvDailyRoi.text = "${df.format(plan.dailyPercentage)}% ROI"
        binding.etAmount.hint = "Min: Rs.${plan.minAmount}"

        // ---------- Enable/Disable buy button ----------
        binding.btnBuy.isEnabled = false
        binding.etAmount.addTextChangedListener { input ->
            val amt = input.toString().toDoubleOrNull() ?: 0.0
            val isAboveMin = amt >= plan.minAmount
            val isBelowMax = plan.maxAmount?.let { amt <= it } ?: true

            binding.btnBuy.isEnabled = isAboveMin && isBelowMax

            if (binding.tilAmount.error != null && isAboveMin && isBelowMax) {
                binding.tilAmount.error = null
            }
        }

        // ---------- Buy button click ----------
        binding.btnBuy.setOnClickListener {
            val amount = binding.etAmount.text.toString().toDoubleOrNull() ?: 0.0

            when {
                amount < plan.minAmount -> {
                    binding.tilAmount.error = "Minimum Amount is Rs.${plan.minAmount}"
                    return@setOnClickListener
                }
                plan.maxAmount != null && amount > plan.maxAmount -> {
                    binding.tilAmount.error = "Maximum Amount is Rs.${plan.maxAmount}"
                    return@setOnClickListener
                }
                else -> binding.tilAmount.error = null
            }

            showLoading()
            binding.btnBuy.isEnabled = false

            lifecycleScope.launch(Dispatchers.Main) {
                val status = withContext(Dispatchers.IO) {
                    repo.buyPlan(uid = uid, pkgId = plan.docId, amount = amount)
                }

                hideLoading()
                binding.btnBuy.isEnabled = true

                when (status) {
                    BuyPlanRepo.Status.SUCCESS -> {
                        showSnack("🎉 Purchase successful!")
                        dismiss()
                    }
                    BuyPlanRepo.Status.MIN_INVEST_ERROR ->
                        showSnack("Minimum investment is Rs.${plan.minAmount}.")
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

    private fun hideLoading() {
        loadingOverlay?.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
