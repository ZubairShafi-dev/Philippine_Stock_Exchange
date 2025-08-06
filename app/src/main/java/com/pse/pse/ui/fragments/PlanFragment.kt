package com.pse.pse.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pse.pse.adapters.PlanAdapter
import com.pse.pse.databinding.FragmentPlanBinding
import com.pse.pse.ui.viewModels.PlansViewModelFactory
import com.yourpackage.data.repository.PlanRepository
import com.yourpackage.ui.viewmodel.PlansViewModel
import kotlinx.coroutines.flow.collectLatest

/**
 * Fragment that lists investment plans.
 * Extends [BaseFragment] so we can reuse showLoading()/hideLoading()/showError().
 */
class PlanFragment : BaseFragment() {

    private var _binding: FragmentPlanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlansViewModel by viewModels {
        PlansViewModelFactory(PlanRepository())
    }

    private lateinit var adapter: PlanAdapter

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

        // ─── RecyclerView setup ────────────────────────────────────────────────
        adapter = PlanAdapter { plan ->
            // TODO: navigate to Buy‑flow or bottom‑sheet
            // findNavController().navigate(...)
        }
        binding.allPlansRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PlanFragment.adapter
        }

        // ─── Collect StateFlow from ViewModel ─────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.state.collectLatest { state ->
                when (state) {
                    is PlansViewModel.UiState.Loading -> showLoading()
                    is PlansViewModel.UiState.Success -> {
                        hideLoading()
                        adapter.submitList(state.plans)
                    }
                    is PlansViewModel.UiState.Error -> {
                        hideLoading()
                        showError(state.throwable.message ?: "Unknown error")
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
