package com.pse.pse.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.pse.pse.data.repository.BuyPlanRepo
import com.pse.pse.databinding.BuyPlanSheetBinding
import com.pse.pse.models.Plan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom-sheet that collects an investment amount and performs the purchase.
 *
 * Validation logic honours:
 *  • Minimum amount (always enforced)
 *  • Maximum amount – only if the plan specifies one (null == unlimited)
 */
class BuyPlanSheet(
    private val plan: Plan,
    private val repo: BuyPlanRepo,
    private val uid: String
) : BottomSheetDialogFragment() {

    private var _binding: BuyPlanSheetBinding? = null
    private val binding get() = _binding!!

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

        /* ---------- 1. Enable button only when amount is valid ---------- */
        binding.btnBuy.isEnabled = false
        binding.etAmount.addTextChangedListener { input ->
            val amt = input.toString().toDoubleOrNull() ?: 0.0

            val isAboveMin = amt >= plan.minAmount
            val isBelowMax = plan.maxAmount?.let { amt <= it } ?: true   // ← NEW

            binding.btnBuy.isEnabled = isAboveMin && isBelowMax

            if (binding.tilAmount.error != null && isAboveMin && isBelowMax) {
                binding.tilAmount.error = null
            }
        }

        /* ---------- 2. Validation on click ---------- */
        binding.btnBuy.setOnClickListener {
            val amount = binding.etAmount.text.toString().toDoubleOrNull() ?: 0.0

            when {
                amount < plan.minAmount -> {
                    binding.tilAmount.error = "Minimum Amount is Rs.${plan.minAmount}"
                    return@setOnClickListener
                }

                plan.maxAmount != null && amount > plan.maxAmount -> {    // ← NEW
                    binding.tilAmount.error = "Maximum Amount is Rs.${plan.maxAmount}"
                    return@setOnClickListener
                }

                else -> binding.tilAmount.error = null
            }

          /*  *//* ---------- 3. Rest of your existing code (unchanged) ---------- *//*
            Log.d(
                "BuyPlanSheet",
                "Calling buyPlan(uid='$uid', pkgId='${plan.docId}', amount=$amount)"
            )
           */

            lifecycleScope.launch(Dispatchers.Main) {
                val status = withContext(Dispatchers.IO) {
                    repo.buyPlan(uid = uid, pkgId = plan.docId, amount = amount)
                }

                Log.d("BuyPlanSheet", "buyPlan returned: $status")
                Toast.makeText(requireContext(), "Result: $status", Toast.LENGTH_SHORT).show()

                when (status) {
                    BuyPlanRepo.Status.SUCCESS -> {
                        Toast.makeText(
                            requireContext(),
                            "🎉 Purchase successful!",
                            Toast.LENGTH_LONG
                        ).show()
                        dismiss()
                    }

                    BuyPlanRepo.Status.MIN_INVEST_ERROR ->
                        Toast.makeText(
                            requireContext(),
                            "Minimum investment is Rs.${plan.minAmount}.",
                            Toast.LENGTH_LONG
                        ).show()

                    BuyPlanRepo.Status.INSUFFICIENT_BALANCE ->
                        Toast.makeText(
                            requireContext(),
                            "Insufficient balance. Please top up and try again.",
                            Toast.LENGTH_LONG
                        ).show()

                    BuyPlanRepo.Status.FAILURE ->
                        Toast.makeText(
                            requireContext(),
                            "Purchase failed. Please try again later.",
                            Toast.LENGTH_LONG
                        ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}