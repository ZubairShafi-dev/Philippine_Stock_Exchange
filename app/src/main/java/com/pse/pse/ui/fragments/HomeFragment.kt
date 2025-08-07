package com.pse.pse.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import com.pse.pse.R
import com.pse.pse.adapters.AnnouncementAdapter
import com.pse.pse.adapters.AnnouncementSliderAdapter
import com.pse.pse.adapters.HomeScreenAdapter
import com.pse.pse.adapters.NotificationAdapter
import com.pse.pse.data.repository.AuthRepository
import com.pse.pse.data.repository.BuyPlanRepo
import com.pse.pse.databinding.DialogeBuyMedicineBinding
import com.pse.pse.databinding.DialogueBuyStockBinding
import com.pse.pse.databinding.FragmentHomeBinding

import com.pse.pse.models.AnnouncementModel
import com.pse.pse.models.PlanModel
import com.pse.pse.ui.viewModels.AccountViewModel
import com.pse.pse.ui.viewModels.PlansViewModelFactory
import com.pse.pse.ui.viewModels.TransactionViewModel
import com.pse.pse.utils.NotificationPreferenceManager
import com.pse.pse.utils.PullToRefreshUtil
import com.pse.pse.utils.SharedPrefManager
import com.pse.pse.utils.Status
import com.yourpackage.data.repository.PlanRepository
import com.yourpackage.ui.viewmodel.PlansViewModel
import `in`.srain.cube.views.ptr.PtrClassicFrameLayout
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
    private val authRepo by lazy { AuthRepository(requireActivity().application) }
    private val repo by lazy { BuyPlanRepo() }
    private val userId by lazy { SharedPrefManager(requireContext()).getId().orEmpty() }
    private val prefs by lazy { SharedPrefManager(requireContext()) }
    private var isDialogShowing = false
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    // For announcement slider auto-scroll
    private val sliderHandler = Handler(Looper.getMainLooper())
    private lateinit var sliderRunnable: Runnable
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
        val planRepo  = PlanRepository()
        val factory  = PlansViewModelFactory(planRepo)

        // ✅ Create the ViewModel with factory
        planViewModel = ViewModelProvider(this, factory)[PlansViewModel::class.java]
        accountViewModel = ViewModelProvider(this)[AccountViewModel::class.java]

        // ✅ Run profit update on app launch
        val userId = SharedPrefManager(requireContext()).getId()



}
}



















