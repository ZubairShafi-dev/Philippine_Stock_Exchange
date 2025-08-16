package com.pse.pse.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.pse.pse.R
import com.pse.pse.adapters.PlanAdapter
import com.pse.pse.data.repository.BuyPlanRepo
import com.pse.pse.databinding.FragmentPlanBinding
import com.pse.pse.models.Plan
import com.pse.pse.ui.viewModels.PlansViewModelFactory
import com.pse.pse.utils.SharedPrefManager
import com.yourpackage.data.repository.PlanRepository
import com.yourpackage.ui.viewmodel.PlansViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlanFragment : Fragment() {

    private var _binding: FragmentPlanBinding? = null
    private val binding get() = _binding!!

    private val plansVm by lazy {
        ViewModelProvider(
            this,
            PlansViewModelFactory(PlanRepository())
        )[PlansViewModel::class.java]
    }

    private val buyRepo = BuyPlanRepo()

    // Shared-pref se UID; FirebaseAuth use karna ho to yahan badal lo
    private val prefs by lazy { SharedPrefManager(requireContext()) }
    private val userId by lazy { prefs.getId().orEmpty() }

    private lateinit var planAdapter: PlanAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Listen for buy result from BottomSheet
        parentFragmentManager.setFragmentResultListener(
            BUY_RESULT_KEY, viewLifecycleOwner
        ) { _, bundle ->
            val success = bundle.getBoolean(BUY_RESULT_SUCCESS, false)
            if (success) {

                // 1) show a host-level snackbar (persists across navigate)
                val hostRoot = requireActivity().findViewById<View>(android.R.id.content)
                Snackbar.make(hostRoot, "🎉 Purchase successful!", Snackbar.LENGTH_LONG).show()

                // Navigate to MyPlans and POP PlanFragment
                val options = NavOptions.Builder()
                    .setPopUpTo(R.id.planFragment, /*inclusive=*/true)
                    .build()
                findNavController().navigate(
                    R.id.planFragment_to_myPlansFragment,
                    null,
                    options
                )
            }
        }

        // RecyclerView setup
        planAdapter = PlanAdapter { plan -> onPlanClicked(plan) }
        binding.allPlansRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = planAdapter
        }

        // Observe plans StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                plansVm.state.collectLatest { ui ->
                    when (ui) {
                        is PlansViewModel.UiState.Loading -> showLoading()
                        is PlansViewModel.UiState.Success -> {
                            hideLoading()
                            planAdapter.submitList(ui.plans.sortedBy { it.minAmount })
                        }

                        is PlansViewModel.UiState.Error -> {
                            hideLoading()
                            Toast.makeText(requireContext(), ui.msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    /** Show the BottomSheet that now handles the entire buy flow */
    private fun onPlanClicked(plan: Plan) {
        BuyPlanSheet(
            plan = plan,
            repo = buyRepo,
            uid = userId
        ).show(parentFragmentManager, "BuyPlanSheet")
    }

    private fun showLoading() = Unit
    private fun hideLoading() = Unit

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val BUY_RESULT_KEY = "buy_plan_result"
        const val BUY_RESULT_SUCCESS = "success"
    }
}