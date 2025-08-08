package com.pse.pse.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.pse.pse.data.repository.BuyPlanRepo
import com.pse.pse.databinding.FragmentHomeBinding
import com.pse.pse.ui.viewModels.AccountViewModel
import com.pse.pse.ui.viewModels.PlansViewModelFactory
import com.pse.pse.utils.SharedPrefManager
import com.yourpackage.data.repository.PlanRepository
import com.yourpackage.ui.viewmodel.PlansViewModel

/**
 * Home screen:
 *  • Greets the user by first name
 *  • Shows full name + real‑time balance in the wallet card
 *  • Retains the existing plan / deposit / withdraw navigation
 */
class HomeFragment : BaseFragment() {

    // ─── ViewBinding ──────────────────────────────────────────────────────────
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // ─── Data layer (no DI / Hilt) ────────────────────────────────────────────
    private lateinit var planViewModel: PlansViewModel
    private lateinit var accountViewModel: AccountViewModel

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)


        val repo = BuyPlanRepo()
        val planRepo = PlanRepository()
        val factory = PlansViewModelFactory(planRepo)

        // ✅ Create the ViewModel with factory
        planViewModel = ViewModelProvider(this, factory)[PlansViewModel::class.java]
        accountViewModel = ViewModelProvider(this)[AccountViewModel::class.java]

        // ✅ Run profit update on app launch
        val userId = SharedPrefManager(requireContext()).getId()


    }
}