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
 * Bottom sheet that collects an investment amount and performs the purchase.
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

        // Start with the Buy button disabled
        binding.btnBuy.isEnabled = false

        // Enable only when entered amount meets the minimum
        binding.etAmount.addTextChangedListener { input ->
            val amt = input.toString().toDoubleOrNull() ?: 0.0
            binding.btnBuy.isEnabled = amt >= plan.minAmount
            if (binding.tilAmount.error != null && amt >= plan.minAmount) {
                binding.tilAmount.error = null
            }
        }

        // Handle Buy click
        binding.btnBuy.setOnClickListener {
            val amount = binding.etAmount.text.toString().toDoubleOrNull() ?: 0.0
            if (amount < plan.minAmount) {
                binding.tilAmount.error = "Minimum Amount is Rs.${plan.minAmount}"
                return@setOnClickListener
            }

            // Debug inputs
            Log.d("BuyPlanSheet", "Calling buyPlan(uid='$uid', pkgId='${plan.docId}', amount=$amount)")
            Toast.makeText(
                requireContext(),
                "Calling buyPlan(uid='$uid', pkgId='${plan.docId}', amount=$amount)",
                Toast.LENGTH_SHORT
            ).show()

            // Launch on Main explicitly
            lifecycleScope.launch(Dispatchers.Main) {
                // Perform purchase off the main thread
                val status = withContext(Dispatchers.IO) {
                    repo.buyPlan(uid = uid, pkgId = plan.docId, amount = amount)
                }

                // Log & toast status
                Log.d("BuyPlanSheet", "buyPlan returned: $status")
                Toast.makeText(
                    requireContext(),
                    "Result: $status",
                    Toast.LENGTH_SHORT
                ).show()

                // Show detailed feedback
                when (status) {
                    BuyPlanRepo.Status.SUCCESS -> {
                        Toast.makeText(
                            requireContext(),
                            "🎉 Purchase successful!", Toast.LENGTH_LONG
                        ).show()
                        dismiss()
                    }
                    BuyPlanRepo.Status.MIN_INVEST_ERROR -> Toast.makeText(
                        requireContext(),
                        "Minimum investment is Rs.${plan.minAmount}.", Toast.LENGTH_LONG
                    ).show()
                    BuyPlanRepo.Status.INSUFFICIENT_BALANCE -> Toast.makeText(
                        requireContext(),
                        "Insufficient balance. Please top up and try again.", Toast.LENGTH_LONG
                    ).show()
                    BuyPlanRepo.Status.FAILURE -> Toast.makeText(
                        requireContext(),
                        "Purchase failed. Please try again later.", Toast.LENGTH_LONG
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
