package com.pse.pse.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.pse.pse.R
import com.pse.pse.data.repository.BuyPlanRepo
import com.pse.pse.databinding.DialogMedicineDetailBinding
import com.pse.pse.databinding.FragmentBoughtMedicinesBinding
import com.pse.pse.models.UserPlanModel
import com.pse.pse.ui.viewModels.PlanViewModel
import com.pse.pse.ui.viewModels.PlanViewModelFactory
import com.pse.pse.utils.SharedPrefManager
import com.pse.pse.adapters.BoughtMedicineAdapter


class BoughtMedicinesFragment : BaseFragment() {

    private lateinit var planViewModel: PlanViewModel
    private var _binding: FragmentBoughtMedicinesBinding? = null
    private val binding get() = _binding!!

    // Declare a key for the scroll position to save and restore separately for this fragment's RecyclerView


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentBoughtMedicinesBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        val userId = SharedPrefManager(requireContext()).getId() ?: ""
        val repo = BuyPlanRepo(requireContext())
        val factory = PlanViewModelFactory(repo)
        planViewModel = ViewModelProvider(this, factory)[PlanViewModel::class.java]

        binding.boughtMedkRV.layoutManager = GridLayoutManager(requireContext(), 2)
        val adapter= BoughtMedicineAdapter(emptyList(), emptyList(), object : BoughtMedicineAdapter.OnItemClickListener {
            override fun onPlanClick(plan: UserPlanModel) {
                showSellBottomSheet(plan)
            }
        })
        binding.boughtMedkRV.adapter = adapter

        planViewModel.fetchUserPlans("medicine_active", userId)
        planViewModel.fetchPlanProgress(userId)
        observeUserPlansAndProgress()

    }

    private fun observeUserPlansAndProgress() {
        // Observe both userPlansLiveData and planProgressLiveData
        val userPlansLiveData = planViewModel.userPlansLiveData
        val planProgressLiveData = planViewModel.planProgressLiveData

        // Observe user plans first
        userPlansLiveData.observe(viewLifecycleOwner) { plansData ->
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
                    expiry_date = it["expiry_date"] as com.google.firebase.Timestamp,
                )
            }

            // Observe plan progress
            planProgressLiveData.observe(viewLifecycleOwner) { progressList ->
                if (progressList.isNotEmpty()) {
                   val adapter = BoughtMedicineAdapter(
                        planModels, progressList,  // Pass both plans and progress to the adapter
                        object : BoughtMedicineAdapter.OnItemClickListener {
                            override fun onPlanClick(plan: UserPlanModel) {
                                showSellBottomSheet(plan)
                            }
                        })
                    binding.boughtMedkRV.adapter = adapter
                }
            }
        }
    }

    private fun showSellBottomSheet(plan: UserPlanModel) {
        val dialog = BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialog)
        val dialogBinding = DialogMedicineDetailBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(true)
        dialog.show()

        dialogBinding.tvTitle.text = plan.plan_name
        dialogBinding.tvSymbol.text = plan.user_id
        dialogBinding.daysProgress
        dialogBinding.tvPrice.text = "$${plan.invested_amount}"
        dialogBinding.tvChange.text = "$%.2f".format(plan.profitTrack)


        dialogBinding.btnBack.setOnClickListener {
            dialog.dismiss()
        }
    }


    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}