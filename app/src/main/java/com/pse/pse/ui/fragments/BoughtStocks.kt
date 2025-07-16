package com.pse.pse.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Timestamp
import com.pse.pse.R
import com.pse.pse.data.repository.BuyPlanRepo
import com.pse.pse.databinding.DialogeSellStockBinding
import com.pse.pse.databinding.FragmentBoughtStocksBinding
import com.pse.pse.models.UserPlanModel
import com.pse.pse.ui.viewModels.PlanViewModel
import com.pse.pse.ui.viewModels.PlanViewModelFactory
import com.pse.pse.utils.SharedPrefManager
import com.pse.pse.utils.Status
import com.trustledger.aitrustledger.viewModel.Adapters.UserPlanAdapter
import kotlinx.coroutines.launch

class BoughtStocks : BaseFragment() {

    private var _binding: FragmentBoughtStocksBinding? = null
    private val binding get() = _binding!!
    private lateinit var planViewModel: PlanViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBoughtStocksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        binding.boughtStockRV.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.boughtStockRV.adapter =
            UserPlanAdapter(emptyList(), object : UserPlanAdapter.OnItemClickListener {
                override fun onPlanClick(plan: UserPlanModel) {
                    // Handle plan click
                }
            })

        val userId = SharedPrefManager(requireContext()).getId() ?: ""
        val repo = BuyPlanRepo(requireContext())
        val factory = PlanViewModelFactory(repo)
        planViewModel = ViewModelProvider(this, factory)[PlanViewModel::class.java]

        planViewModel.fetchUserPlans("stock_open", userId)
        observeUserPlans()
    }

    private fun observeUserPlans() {
        planViewModel.userPlansLiveData.observe(viewLifecycleOwner) { plansData ->

            val planModels = plansData.map {
                UserPlanModel(
                    user_id = it["user_id"] as? String ?: "",
                    plan_name = it["plan_name"] as? String ?: "",
                    invested_amount = (it["invested_amount"] as? Number)?.toDouble() ?: 0.0,
                    direct_profit = (it["direct_profit"] as? Number)?.toDouble() ?: 0.0,
                    daily_profit = (it["daily_profit"] as? Number)?.toDouble() ?: 0.0,
                    percentage = (it["percentage"] as? Number)?.toDouble() ?: 0.0,
                    directProfitPercent = (it["directProfitPercent"] as? Number)?.toDouble() ?: 0.0,
                    profitTrack = (it["profitTrack"] as? Number)?.toDouble() ?: 0.0,
                    status = it["status"] as? String ?: "",
                    docId = it["docId"] as? String ?: "",
                    expiry_date = it["expiry_date"] as Timestamp,
                )
            }

            val adapter = UserPlanAdapter(planModels, object : UserPlanAdapter.OnItemClickListener {
                override fun onPlanClick(plan: UserPlanModel) {
                    showSellBottomSheet(plan)
                }
            })
            binding.boughtStockRV.adapter = adapter
        }
    }

    private fun showSellBottomSheet(plan: UserPlanModel) {
        val dialog = BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialog)
        val dialogBinding = DialogeSellStockBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(true)
        dialog.show()

        dialogBinding.tvTitle.text = plan.plan_name
        dialogBinding.tvSymbol.text = plan.user_id
        dialogBinding.etQnty.setText(plan.invested_amount.toString())
        dialogBinding.etQnty.isEnabled = false
        dialogBinding.amountValueTV.text = "$${plan.invested_amount}"

        dialogBinding.btnSell.setOnClickListener {
            dialog.dismiss()
            showLoading()

            lifecycleScope.launch {
                try {
                    val status = planViewModel.sellPlan(plan.docId)
                    when (status) {
                        Status.SUCCESS -> Toast.makeText(
                            requireContext(), "Stock sold successfully!", Toast.LENGTH_SHORT
                        ).show()

                        Status.NO_PLAN_FOUND -> Toast.makeText(
                            requireContext(), "Plan not found.", Toast.LENGTH_SHORT
                        ).show()

                        Status.NO_USER_FOUND -> Toast.makeText(
                            requireContext(), "User not found.", Toast.LENGTH_SHORT
                        ).show()

                        Status.ERROR -> Toast.makeText(
                            requireContext(), "An error occurred.", Toast.LENGTH_SHORT
                        ).show()

                        else -> Toast.makeText(
                            requireContext(), "Unknown result: $status", Toast.LENGTH_SHORT
                        ).show()
                    }
                } finally {
                    hideLoading()
                }
            }
        }

        dialogBinding.btnBack.setOnClickListener {
            dialog.dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}