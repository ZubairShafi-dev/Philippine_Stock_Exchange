package com.pse.pse.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.pse.pse.data.repository.BuyPlanRepo
import com.pse.pse.databinding.FragmentForexInvestmentBinding
import com.pse.pse.models.PlanModel
import com.pse.pse.ui.viewModels.PlanViewModel
import com.pse.pse.ui.viewModels.PlanViewModelFactory
import com.pse.pse.utils.Status
import com.trustledger.aitrustledger.R
import com.pse.pse.adapters.ForexPlanAdapter
import com.trustledger.aitrustledger.databinding.DialogeBuyMedicineBinding
import kotlinx.coroutines.launch

class ForexInvestmentFragment : BaseFragment() {

    private var _binding: FragmentForexInvestmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var planViewModel: PlanViewModel
    private lateinit var adapter: ForexPlanAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForexInvestmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)


        val repository = BuyPlanRepo(requireContext())
        val factory = PlanViewModelFactory(repository)
        planViewModel = ViewModelProvider(this, factory)[PlanViewModel::class.java]

        // Set GridLayoutManager with 2 columns
        binding.forexRecyclerPlans.layoutManager = GridLayoutManager(requireContext(), 1)

        // Observe plans from ViewModel
        forexPlan()
    }

    private fun forexPlan() {
        planViewModel.getPlans().observe(viewLifecycleOwner, Observer { fetchedPlans ->
            if (fetchedPlans != null && fetchedPlans.isNotEmpty()) {
                adapter = ForexPlanAdapter(fetchedPlans) { selectedPlan ->
                    showBuyForexBottomSheet(plan = selectedPlan)

                }
                binding.forexRecyclerPlans.adapter = adapter
            } else {
                Toast.makeText(requireContext(), "No plans available", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showBuyForexBottomSheet(plan: PlanModel) {
        val dialog = BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialog)
        val binding = DialogeBuyMedicineBinding.inflate(layoutInflater)

        dialog.setContentView(binding.root)
        dialog.setCancelable(true)
        dialog.show()

        // Set plan info
        binding.tvTitle.text = plan.planName
        binding.tvSymbol.text = plan.type
        binding.tvPrice.text = "$${plan.minAmount}" // Set fixed amount display (if available)
        // Option 1: Kotlin string-format
        binding.tvChange.text = "+%.2f%%".format(plan.dailyPercentage)

        binding.PriceET.setText(plan.minAmount.toString()) // Pre-fill min amount for user


        val buyPlanRepo = BuyPlanRepo(requireContext())

        // Handle Buy click
        binding.btnBuy.setOnClickListener {
            val enteredAmountText = binding.PriceET.text.toString().replace("$", "").trim()


            // Validate if entered amount is a valid number
            if (enteredAmountText.isEmpty() || enteredAmountText.toDoubleOrNull() == null) {
                Toast.makeText(requireContext(), "Please enter a valid amount!", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            val investedAmount = enteredAmountText.toDouble()

            fun toast(msg: String) =
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

            // 1.  Make sure the plan HAS a minimum
            val min = plan.minAmount ?: run {
                toast("Plan data is incomplete. Please try again later.")
                return@setOnClickListener
            }

            // 2.  Validate the user’s input
            when {
                investedAmount <= 0.0 -> {
                    toast("Please enter an amount greater than zero.")
                    return@setOnClickListener
                }

                investedAmount < min -> {
                    toast("Minimum investment amount is $min.")
                    return@setOnClickListener
                }
            }


            Log.d("StockInvestment", "Invested Amount: $investedAmount")

            // Dismiss the dialog first
            dialog.dismiss()
            // Show the loading overlay
            showLoading()

            lifecycleScope.launch {
                try {
                    val status = buyPlanRepo.buyPlan(
                        investedAmount, // ✅ Auto use amount from plan
                        planName = plan.planName.toString()
                    )

                    when (status) {
                        Status.SUCCESS -> {
                            Toast.makeText(
                                requireContext(), "Plan bought successfully!", Toast.LENGTH_SHORT
                            ).show()
                        }

                        Status.NOT_ENOUGH_BALANCE -> {
                            Toast.makeText(
                                requireContext(), "Insufficient balance!", Toast.LENGTH_SHORT
                            ).show()
                        }

                        Status.INVALID_AMOUNT -> {
                            Toast.makeText(requireContext(), "Invalid amount!", Toast.LENGTH_SHORT)
                                .show()
                        }

                        else -> {
                            Toast.makeText(
                                requireContext(),
                                "Failed to buy plan!",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }
                    }
                } finally {
                    // Hide the loading overlay regardless of outcome
                    hideLoading()
                }
            }
        }

        binding.btnBack.setOnClickListener {
            dialog.dismiss()
        }
    }
}
