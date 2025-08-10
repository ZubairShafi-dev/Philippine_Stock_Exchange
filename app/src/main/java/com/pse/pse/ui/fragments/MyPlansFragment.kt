package com.pse.pse.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.pse.pse.adapters.UserPlanAdapter
import com.pse.pse.databinding.FragmentMyPlansBinding
import com.pse.pse.ui.viewModels.PlansViewModelFactory
import com.pse.pse.utils.SharedPrefManager
import com.yourpackage.data.repository.PlanRepository
import com.yourpackage.ui.viewmodel.PlansViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MyPlansFragment : BaseFragment() {

    private var _b: FragmentMyPlansBinding? = null
    private val b get() = _b!!

    // Reuse existing VM + factory
    private val vm: PlansViewModel by viewModels {
        PlansViewModelFactory(PlanRepository())
    }

    private lateinit var adapter: UserPlanAdapter
    private var currentTab = 0 // 0 active, 1 expired

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentMyPlansBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        adapter = UserPlanAdapter()
        b.rvMyPlans.layoutManager = LinearLayoutManager(requireContext())
        b.rvMyPlans.adapter = adapter

        val uid = SharedPrefManager(requireContext()).getId().orEmpty()
        vm.bindUser(uid)

        fun collectActive() {
            viewLifecycleOwner.lifecycleScope.launch {
                vm.activeUserPlans(uid).collectLatest { list ->
                    render(list.isNotEmpty(), list)
                }
            }
        }

        fun collectExpired() {
            viewLifecycleOwner.lifecycleScope.launch {
                vm.expiredUserPlans(uid).collectLatest { list ->
                    render(list.isNotEmpty(), list)
                }
            }
        }

        // default tab = Active
        collectActive()

        b.tabPlans.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                if (currentTab == 0) collectActive() else collectExpired()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun render(hasData: Boolean, list: List<com.pse.pse.models.UserPlanUi>) {
        b.emptyState.isVisible = !hasData
        b.rvMyPlans.isGone = !hasData
        if (hasData) adapter.submit(list)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}