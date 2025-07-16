package com.pse.pse.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Timestamp
import com.pse.pse.R
import com.pse.pse.data.repository.BuyPlanRepo
import com.pse.pse.databinding.DialogeSellStockBinding
import com.pse.pse.databinding.FragmentBoughtForexBinding
import com.pse.pse.models.UserPlanModel
import com.pse.pse.ui.viewModels.PlanViewModel
import com.pse.pse.ui.viewModels.PlanViewModelFactory
import com.pse.pse.utils.SharedPrefManager
import com.pse.pse.utils.Status
import com.trustledger.aitrustledger.R
import com.trustledger.aitrustledger.adapters.BoughtForexAdapter
import com.trustledger.aitrustledger.databinding.DialogeSellStockBinding
import kotlinx.coroutines.launch


class BoughtForexFragment : BaseFragment() {

    private lateinit var planViewModel: PlanViewModel
    private var _binding: FragmentBoughtForexBinding? = null
    private val binding get() = _binding!!



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentBoughtForexBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)
        val repo = BuyPlanRepo(requireContext())
        val factory = PlanViewModelFactory(repo)
        planViewModel = ViewModelProvider(this, factory)[PlanViewModel::class.java]
        val userId = SharedPrefManager(requireContext()).getId() ?: ""
        binding.boughtMedkRV.layoutManager = LinearLayoutManager(requireContext())

        planViewModel.fetchUserPlans("active", userId)
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
            val onInvestClick: (UserPlanModel) -> Unit = { plan ->
                showSellBottomSheet(plan)
            }
            binding.boughtMedkRV.adapter =
                BoughtForexAdapter(planModels, onInvestClick)

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
                    val status = planViewModel.sellForex(plan.docId)
                    when (status) {
                        Status.SUCCESS -> {
                            Toast.makeText(
                                requireContext(),
                                "Forex plan sold successfully!",
                                Toast.LENGTH_SHORT
                            ).show()
                            // 🔥 RE-FETCH USER PLANS TO UPDATE THE LIST
                            val userId = SharedPrefManager(requireContext()).getId() ?: ""
                            planViewModel.fetchUserPlans("active", userId)
                        }

                        Status.NO_PLAN_FOUND -> Toast.makeText(
                            requireContext(),
                            "Plan not found.",
                            Toast.LENGTH_SHORT
                        ).show()

                        Status.NO_USER_FOUND -> Toast.makeText(
                            requireContext(),
                            "User not found.",
                            Toast.LENGTH_SHORT
                        ).show()

                        Status.ERROR -> Toast.makeText(
                            requireContext(),
                            "An error occurred.",
                            Toast.LENGTH_SHORT
                        ).show()

                        else -> Toast.makeText(
                            requireContext(),
                            "Unknown result: $status",
                            Toast.LENGTH_SHORT
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
        _binding = null
        super.onDestroyView()
    }
}