package com.pse.pse.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentResultListener
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
import com.pse.pse.viewModels.PlansViewModelFactory
import com.pse.pse.utils.SharedPrefManager
import com.yourpackage.data.repository.PlanRepository
import com.yourpackage.ui.viewmodel.PlansViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlanFragment : BaseFragment() {

    private var _binding: FragmentPlanBinding? = null
    private val binding get() = _binding!!

    private val plansVm by lazy {
        ViewModelProvider(
            this,
            PlansViewModelFactory(PlanRepository())
        )[PlansViewModel::class.java]
    }

    private val buyRepo = BuyPlanRepo()

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
        // Drawer trigger + (optional) avatar via BaseFragment
        setupDrawerTrigger(binding.root)

        // Listen for buy result from BuyPlanFragment
        parentFragmentManager.setFragmentResultListener(
            BUY_RESULT_KEY,
            viewLifecycleOwner,
            FragmentResultListener { _, bundle ->
                val success = bundle.getBoolean(BUY_RESULT_SUCCESS, false)
                if (success) {
                    val hostRoot = requireActivity().findViewById<View>(android.R.id.content)
                    Snackbar.make(hostRoot, "🎉 Purchase successful!", Snackbar.LENGTH_LONG).show()

                    val options = NavOptions.Builder()
                        .setPopUpTo(R.id.planFragment, /*inclusive=*/true)
                        .build()
                    findNavController().navigate(
                        R.id.action_planFragment_to_myPlansFragment,
                        null,
                        options
                    )
                }
            }
        )

        // RecyclerView
        planAdapter = PlanAdapter { plan -> onPlanClicked(plan) }
        binding.allPlansRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = planAdapter
        }

        // Observe VM
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

    /** Open full-screen BuyPlanFragment (replacing BottomSheet). */
    private fun onPlanClicked(plan: Plan) {
        // Pass only the fields we actually use in the buy screen (no model change needed).
        val args = bundleOf(
            BuyPlanFragment.ARG_PLAN_NAME to plan.planName,
            BuyPlanFragment.ARG_MIN_AMOUNT to plan.minAmount,
            BuyPlanFragment.ARG_MAX_AMOUNT to (plan.maxAmount ?: -1.0), // -1 → "No limit"
            BuyPlanFragment.ARG_DAILY_PERCENT to plan.dailyPercentage,
            BuyPlanFragment.ARG_TOTAL_PAYOUT to plan.totalPayout,
            BuyPlanFragment.ARG_DOC_ID to plan.docId,
            BuyPlanFragment.ARG_UID to userId
        )
        findNavController().navigate(R.id.action_planFragment_to_buyPlanFragment, args)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val BUY_RESULT_KEY = "buy_plan_result"
        const val BUY_RESULT_SUCCESS = "success"
    }
}