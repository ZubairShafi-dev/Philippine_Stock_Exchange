package com.pse.pse.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.pse.pse.adapters.PlanAdapter
import com.pse.pse.data.repository.PlanRepository
import com.pse.pse.databinding.FragmentPlanBinding
import com.pse.pse.ui.viewModels.PlansViewModel
import com.pse.pse.ui.viewModels.PlansViewModelFactory


class PlanFragment : BaseFragment() {


    private lateinit var plansViewModel: PlansViewModel
    private var _binding: FragmentPlanBinding? = null
    private val binding get() = _binding!!
    private lateinit var planAdapter: PlanAdapter


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repository = PlanRepository()
        plansViewModel = ViewModelProvider(this, PlansViewModelFactory(repository))[PlansViewModel::class.java]

        planAdapter = PlanAdapter {}
        binding.allPlansRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = planAdapter
        }

        plansViewModel.fetchPlans()

        plansViewModel.plans.observe(viewLifecycleOwner) {
            planAdapter.submitList(it)
        }
    }
}