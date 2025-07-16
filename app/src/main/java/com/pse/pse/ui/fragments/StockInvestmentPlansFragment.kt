package com.trustledger.aitrustledger.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.pse.pse.ui.fragments.BaseFragment
import com.trustledger.aitrustledger.R
import com.trustledger.aitrustledger.data.repository.BuyPlanRepo
import com.trustledger.aitrustledger.databinding.DialogueBuyStockBinding
import com.trustledger.aitrustledger.databinding.FragmentStockInvestmentPlanBinding
import com.trustledger.aitrustledger.models.AccountModel
import com.trustledger.aitrustledger.models.PlanModel
import com.trustledger.aitrustledger.ui.viewModels.AccountViewModel
import com.trustledger.aitrustledger.ui.viewModels.PlanViewModel
import com.trustledger.aitrustledger.ui.viewModels.PlanViewModelFactory
import com.trustledger.aitrustledger.utils.SharedPrefManager
import com.trustledger.aitrustledger.utils.Status
import com.trustledger.aitrustledger.viewModel.Adapters.StocksPlanAdapter
import kotlinx.coroutines.launch

class StockInvestmentPlansFragment : BaseFragment(), StocksPlanAdapter.OnItemClickListener {

    private var _binding: FragmentStockInvestmentPlanBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedPrefManager: SharedPrefManager

    private lateinit var planViewModel: PlanViewModel
    private val accountViewModel: AccountViewModel by viewModels()
    private lateinit var adapter: StocksPlanAdapter
    private var userAccount: AccountModel? = null
    private lateinit var buyPlanRepo: BuyPlanRepo

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStockInvestmentPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        sharedPrefManager = SharedPrefManager(requireContext())

        // Initialize BuyPlanRepo and ViewModel with factory
        buyPlanRepo = BuyPlanRepo(requireContext().applicationContext)
        val factory = PlanViewModelFactory(buyPlanRepo)
        planViewModel = ViewModelProvider(this, factory)[PlanViewModel::class.java]

        accountViewModel.getAccount(sharedPrefManager.getId()!!)
            .observe(viewLifecycleOwner) { account ->
                userAccount = account
            }

        binding.stockRecyclerPlans.layoutManager = GridLayoutManager(requireContext(), 2)
        stockPlans()
    }

    private fun stockPlans() {
        planViewModel.getPlans().observe(viewLifecycleOwner) { fetchedPlans ->
            if (fetchedPlans != null && fetchedPlans.isNotEmpty()) {
                adapter = StocksPlanAdapter(fetchedPlans, this)
                binding.stockRecyclerPlans.adapter = adapter
            } else {
                Toast.makeText(requireContext(), "No plans available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onPlanClick(plan: PlanModel) {
        showBuyDetailsBottomSheet(plan)
    }

    @SuppressLint("DefaultLocale")
    private fun showBuyDetailsBottomSheet(plan: PlanModel) {
        val secondBottomSheet = BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialog)
        val binding = DialogueBuyStockBinding.inflate(layoutInflater)
        secondBottomSheet.setContentView(binding.root)
        secondBottomSheet.setCancelable(true)
        secondBottomSheet.show()

        binding.tvTitle.text = plan.planName
        binding.tvSymbol.text = plan.type
        binding.PriceET.setText("${plan.minAmount}") // Pre-fill min amount for user
        binding.amountValueTV.text = "${plan.minAmount}"

        binding.btnBack.setOnClickListener {
            secondBottomSheet.dismiss()
        }

        binding.btnBuy.setOnClickListener {
            val enteredAmountText = binding.PriceET.text.toString().replace("$", "").trim()
            // Pre-fill min amount for user

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

            // Dismiss the dialog before showing the loading overlay
            secondBottomSheet.dismiss()

            // Show the loading overlay using BaseFragment method
            showLoading()

            lifecycleScope.launch {
                try {
                    Log.d(
                        "StockInvestment",
                        "Calling buyPlan with amount: $investedAmount and plan: ${plan.planName}"
                    )

                    val status = buyPlanRepo.buyStock(
                        amount = investedAmount, stockSymbol = plan.planName.toString()
                    )

                    Log.d("StockInvestment", "Buy Plan status: $status")

                    when (status) {
                        Status.SUCCESS -> {
                            Toast.makeText(
                                requireContext(), "Plan purchased successfully!", Toast.LENGTH_SHORT
                            ).show()
                        }

                        Status.NOT_ENOUGH_BALANCE -> {
                            Toast.makeText(requireContext(), "Low Balance!", Toast.LENGTH_SHORT)
                                .show()
                        }

                        Status.INVALID_AMOUNT -> {
                            Toast.makeText(
                                requireContext(), "Invalid investment amount!", Toast.LENGTH_SHORT
                            ).show()
                        }

                        else -> {
                            Toast.makeText(
                                requireContext(),
                                "Something went wrong! Status: $status",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } finally {
                    // Hide the loading overlay regardless of the outcome
                    hideLoading()
                }
            }
        }
    }
}