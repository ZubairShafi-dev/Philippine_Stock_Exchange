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
import com.trustledger.aitrustledger.R
import com.trustledger.aitrustledger.adapters.AnnouncementAdapter
import com.trustledger.aitrustledger.adapters.AnnouncementSliderAdapter
import com.trustledger.aitrustledger.adapters.HomeScreenAdapter
import com.trustledger.aitrustledger.adapters.NotificationAdapter
import com.pse.pse.data.repository.AuthRepository
import com.pse.pse.data.repository.BuyPlanRepo
import com.pse.pse.databinding.FragmentHomeBinding
import com.trustledger.aitrustledger.databinding.DialogeBuyMedicineBinding
import com.trustledger.aitrustledger.databinding.DialogueBuyStockBinding
import com.pse.pse.models.AnnouncementModel
import com.pse.pse.models.PlanModel
import com.pse.pse.ui.viewModels.AccountViewModel
import com.pse.pse.ui.viewModels.PlanViewModel
import com.pse.pse.ui.viewModels.PlanViewModelFactory
import com.pse.pse.ui.viewModels.TransactionViewModel
import com.pse.pse.utils.NotificationPreferenceManager
import com.pse.pse.utils.PullToRefreshUtil
import com.pse.pse.utils.SharedPrefManager
import com.pse.pse.utils.Status
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
    private val txnVM: TransactionViewModel by viewModels()
    private lateinit var planViewModel: PlanViewModel
    private lateinit var accountViewModel: AccountViewModel
    private val authRepo by lazy { AuthRepository(requireActivity().application) }
    private val repo by lazy { BuyPlanRepo(requireContext()) }
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
        setupNetworkMonitoring()

        // ✅ Create the repo and factory
        val repo = BuyPlanRepo(requireContext())
        val factory = PlanViewModelFactory(repo)

        // ✅ Create the ViewModel with factory
        planViewModel = ViewModelProvider(this, factory)[PlanViewModel::class.java]
        accountViewModel = ViewModelProvider(this)[AccountViewModel::class.java]

        // ✅ Run profit update on app launch
        val userId = SharedPrefManager(requireContext()).getId()

        /* binding.walletCard.tvCardNumber.text = userId.toString()*/
        binding.forexRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.topMedicinesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.topStocksRecycler.layoutManager = LinearLayoutManager(requireContext())
        val onInvestClick: (PlanModel) -> Unit = { plan ->
            navigateToInvestmentScreen(plan)
        }
        binding.forexRecycler.adapter = HomeScreenAdapter(emptyList(), "Forex", onInvestClick)
        binding.topMedicinesRecycler.adapter =
            HomeScreenAdapter(emptyList(), "Medicine", onInvestClick)
        binding.topStocksRecycler.adapter = HomeScreenAdapter(emptyList(), "Stocks", onInvestClick)



        planViewModel.initUserId(userId.toString())
        observeWalletTotals()
        planViewModel.fetchFilteredPlans()
        planViewModel.allPlans.observe(viewLifecycleOwner) { plans ->
            if (plans != null) {
                val stockPlans = plans.filter { it.type == "Stocks" }
                val medicinePlans = plans.filter { it.type == "Medicine" }
                val forexPlans = plans.filter { it.type == "Forex" }

                Log.d(
                    "HomeFragment",
                    "Stocks: ${stockPlans.size}, Medicine: ${medicinePlans.size}, Forex: ${forexPlans.size}"
                )

                binding.forexRecycler.adapter =
                    HomeScreenAdapter(forexPlans, "Forex", onInvestClick)
                binding.topMedicinesRecycler.adapter =
                    HomeScreenAdapter(medicinePlans, "Medicine", onInvestClick)
                binding.topStocksRecycler.adapter =
                    HomeScreenAdapter(stockPlans, "Forex", onInvestClick)

                binding.forexRecycler.adapter?.notifyDataSetChanged()
                binding.topMedicinesRecycler.adapter?.notifyDataSetChanged()
                binding.topMedicinesRecycler.adapter?.notifyDataSetChanged()
            }
        }

        binding.announcementIcon.setOnClickListener {
            showLoading()
            accountViewModel.getAnnouncements()
            accountViewModel.announcementLiveData.observe(viewLifecycleOwner) { announcements ->
                if (!announcements.isNullOrEmpty() && !isDialogShowing) {
                    isDialogShowing = true
                    hideLoading()
                    showAnnouncementsDialog(announcements)

                } else if (announcements.isNullOrEmpty()) {
                    hideLoading()
                    Toast.makeText(requireContext(), "No announcements found", Toast.LENGTH_SHORT)
                        .show()
                }
            }

        }

        setClickListeners()
        observeBalance()
        loadProfile()                       // one‑shot name + full name
        txnVM.loadCurrentBalance()          // one‑shot balance
        loadEarnings()

        // ─── Announcement Slider (ViewPager2 + TabLayout) ───────────────────────
        val viewPager = binding.announcementSlider
        val tabLayout = binding.sliderDots

        // 1) Create list + adapter
        val sliderUrls = mutableListOf<String>()
        val sliderAdapter = AnnouncementSliderAdapter(sliderUrls)
        viewPager.adapter = sliderAdapter

        // 2) Attach TabLayout “dots”
        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()

        // 3) Fetch image URLs from ViewModel
        accountViewModel.getAnnouncementImageUrls()

        // 4) Observe LiveData<List<String>>
        accountViewModel.announcementImageUrls.observe(viewLifecycleOwner, Observer { urls ->
            if (!urls.isNullOrEmpty()) {
                viewPager.visibility = View.VISIBLE
                tabLayout.visibility = View.VISIBLE
                binding.viewpagerContainer.visibility = View.VISIBLE

                sliderUrls.clear()
                sliderUrls.addAll(urls)
                sliderAdapter.notifyDataSetChanged()

                // Set up auto-scroll now that pages exist
                sliderRunnable = Runnable {
                    val nextIndex = (viewPager.currentItem + 1) % sliderUrls.size
                    viewPager.currentItem = nextIndex
                    sliderHandler.postDelayed(sliderRunnable, 5000)
                }
                sliderHandler.removeCallbacks(sliderRunnable)
                sliderHandler.postDelayed(sliderRunnable, 3000)
            } else {
                binding.viewpagerContainer.visibility = View.GONE
                viewPager.visibility = View.GONE
                tabLayout.visibility = View.GONE
                // No URLs or error: stop auto-scroll
                if (this::sliderRunnable.isInitialized) {
                    sliderHandler.removeCallbacks(sliderRunnable)
                }
            }
        })
        binding.inputReferral.apply {
            // 1) Display the link and keep it non-editable
            val referralLink = "http://aitrustledger.com/?ref=$userId"
            setText(referralLink)
            setTextColor(resources.getColor(android.R.color.holo_blue_bright))
            isFocusable = false

            // 2) When the user taps the link-icon (drawableEnd), copy the full referralLink
            setOnTouchListener { v, motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_UP) {
                    // compoundDrawablesRelative[2] is the drawableEnd (ic_link)
                    val drawableEnd = compoundDrawablesRelative[2]
                    drawableEnd?.let {
                        // Calculate the touchable area for the icon:
                        val touchableStart = right - paddingEnd - it.intrinsicWidth
                        if (motionEvent.rawX >= touchableStart) {
                            // Copy referralLink into clipboard
                            val clipboard =
                                requireContext().getSystemService(ClipboardManager::class.java)
                            val clip = ClipData.newPlainText("Referral Link", referralLink)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(
                                requireContext(),
                                "Link copied to clipboard",
                                Toast.LENGTH_SHORT
                            ).show()

                            // ─── IMPORTANT: call performClick() for accessibility/lint
                            v.performClick()

                            return@setOnTouchListener true
                        }
                    }
                }
                false
            }
        }

// ─── Display raw referral code (userId) and set up “Copy Code” tap ────
        binding.textReferralCode.text = userId
        (binding.textReferralCode.parent as? View)?.setOnClickListener {
            val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
            val clip = ClipData.newPlainText("Referral Code", userId)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Code copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        setupPullToRefresh()
    }

    override fun onStart() {
        super.onStart()
        txnVM.startBalanceSync()            // start real‑time listener
    }

    override fun onStop() {
        txnVM.stopBalanceSync()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        isDialogShowing = false
    }

    override fun onDestroyView() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (ex: IllegalArgumentException) {
            // callback was never registered or already unregistered – ignore
        }
        _binding = null
        super.onDestroyView()
    }

    private fun setupPullToRefresh() {
        val ptrFrameLayout = binding.root.findViewById<PtrClassicFrameLayout>(R.id.ultra_ptr)
        PullToRefreshUtil.setupUltraPullToRefresh(ptrFrameLayout) {
            // 1) Refresh wallet balance
            txnVM.loadCurrentBalance()
            // 2) Refresh plan lists
            planViewModel.fetchFilteredPlans()
            // 3) Refresh earnings cards
            loadEarnings()
            // 4) Refresh pending transactions (e.g. to catch newly approved deposits)

            // 5) Refresh user greeting & profile info
            loadProfile()
        }
    }

    private fun setupNetworkMonitoring() {
        connectivityManager =
            requireContext().getSystemService(ConnectivityManager::class.java)

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("Network", "Back online")
                requireActivity().runOnUiThread { hideOfflineOverlay() }
            }

            override fun onLost(network: Network) {
                Log.d("Network", "Lost connectivity")
                requireActivity().runOnUiThread { showOfflineOverlay() }
            }
        }

        val request = NetworkRequest.Builder().build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // initial check
        if (!isNetworkAvailable()) {
            Log.d("Network", "Initial state: offline")
            showOfflineOverlay()
        }
    }

    private fun showOfflineOverlay() {
        _binding?.let { bind ->
            bind.noInternetOverlay.visibility = View.VISIBLE
            bind.noInternetAnimation.takeIf { !it.isAnimating }?.playAnimation()

            requireActivity().window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
        }
    }

    private fun hideOfflineOverlay() {
        _binding?.let { bind ->
            bind.noInternetOverlay.visibility = View.GONE
            requireActivity().window.clearFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }


    // ─── Navigation helpers ───────────────────────────────────────────────────
    private fun setClickListeners() = with(binding) {
        itemStockPlan.setOnClickListener { navigateToPlan("Stock") }
        itemMedicinePlan.setOnClickListener { navigateToPlan("Medicine") }
        itemForexPlan.setOnClickListener { navigateToPlan("Forex") }
        withdrawAmount.itemWithdrawAmount.setOnClickListener { findNavController().navigate(R.id.action_home_to_withdrawAmount) }
        depositAmount.itemDepositAmount.setOnClickListener { findNavController().navigate(R.id.action_home_to_depositAmount) }
        boughtPlans.itemPlansBought.setOnClickListener { findNavController().navigate(R.id.action_home_to_plans) }

        notificationIcon.setOnClickListener { showNotificationsDialog() }
        seeAllStocks.setOnClickListener { findNavController().navigate(R.id.action_homeFragment_to_stockInvestmentPlansFragment) }
        seeAllMedicines.setOnClickListener { findNavController().navigate(R.id.action_homeFragment_to_medicineInvestmenetPlanFragment) }
        seeAllForex.setOnClickListener { findNavController().navigate(R.id.action_homeFragment_to_forexInvestmentFragment) }
    }

    private fun navigateToPlan(plan: String) {
        val dest = when (plan) {
            "Stock" -> R.id.action_homeFragment_to_boughtStocksFragment
            "Medicine" -> R.id.action_homeFragment_to_boughtMedicinesFragment
            else -> R.id.action_homeFragment_to_boughtForexFragment
        }
        findNavController().navigate(dest, Bundle().apply { putString("clickedPlan", plan) })
    }

    // ─── UI data binding ──────────────────────────────────────────────────────
    /** Live‑updates wallet amount whenever TransactionRepository pushes changes. */
    private fun observeBalance() {
        txnVM.currentBalance.observe(viewLifecycleOwner) { bal ->
            val amount = bal ?: 0.0
            // wallet card’s amount TextView lives inside the <include>
            val tvAmount = binding.walletCard.tvAmount
            tvAmount.text = "$%.2f".format(amount)
        }
    }

    /** Loads profile once → sets greeting & full name. */
    private fun loadProfile() {
        val uid = prefs.getId() ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            authRepo.getUserProfile(uid)?.let { user ->

                // “Hi, <first‑name>”
                _binding?.let {
                    it.profileTitle.text = "Hi, ${user.name ?: "User"}"

                    // wallet card → “<first-name> <last-name>”
                    val fullName = listOfNotNull(user.name, user.lastName).joinToString(" ")
                    val tvWalletType = it.walletCard.tvWalletType
                    tvWalletType.text = fullName.ifBlank { "—" }
                }
            }
        }
    }

    private fun loadEarnings() {
        val userId = prefs.getId() ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val accountSnapshot = FirebaseFirestore.getInstance().collection("accounts")
                .whereEqualTo("userId", userId).get().await()

            val accountDoc = accountSnapshot.documents.firstOrNull() ?: return@launch
            val earnings = accountDoc["earnings"] as? Map<*, *> ?: return@launch

            val totalEarned = (earnings["totalEarned"] as? Number)?.toDouble() ?: 0.0
            val teamProfit = (earnings["teamProfit"] as? Number)?.toDouble() ?: 0.0
            val referralProfit = (earnings["referralProfit"] as? Number)?.toDouble() ?: 0.0
            val dailyProfit = (earnings["dailyProfit"] as? Number)?.toDouble() ?: 0.0

            updateEarningCard(
                binding.earningsGrid.getChildAt(0), "Today's Earning", dailyProfit, totalEarned
            )
            updateEarningCard(
                binding.earningsGrid.getChildAt(1), "Total Earning", totalEarned, totalEarned
            )
            updateEarningCard(
                binding.earningsGrid.getChildAt(2), "Referral Earning", referralProfit, totalEarned
            )
            updateEarningCard(
                binding.earningsGrid.getChildAt(3), "Team Earning", teamProfit, totalEarned
            )
        }
    }

    private fun updateEarningCard(view: View, title: String, value: Double, maxValue: Double) {
        val titleView = view.findViewById<TextView>(R.id.earningTitle)
        val amountView = view.findViewById<TextView>(R.id.earningAmount)
        val progressBar = view.findViewById<ProgressBar>(R.id.earningProgress)

        titleView.text = title
        amountView.text = "$%.2f".format(value)

        progressBar.max = maxValue.toInt().coerceAtLeast(1) // prevent divide-by-zero
        progressBar.progress = value.toInt()
    }

    private fun showAnnouncementsDialog(announcements: List<AnnouncementModel>) {
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialogue_announcement, null)
        (dialogView.parent as? ViewGroup)?.removeView(dialogView)

        val rv = dialogView.findViewById<RecyclerView>(R.id.announcementRv)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = AnnouncementAdapter(announcements)

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered
        ).setView(dialogView).create()



        dialog.setCancelable(true)
        dialog.setOnDismissListener {
            isDialogShowing = false
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.apply {
            height = 1200
            width = LinearLayout.LayoutParams.MATCH_PARENT
            dialog.window?.attributes = this
        }
        dialog.setCancelable(true)
        dialog.show()

    }

    private fun showNotificationsDialog() {
        Log.d("HomeFragment", "showNotificationsDialog called")  // Debugging line

        // Inflate the dialog layout
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialoge_notification, null)
        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered
        ).setView(dialogView).create()

        val rv = dialogView.findViewById<RecyclerView>(R.id.notificationRv)
        val clearButton = dialogView.findViewById<TextView>(R.id.clearNotificationView)
        rv.layoutManager = LinearLayoutManager(requireContext())

        // Check if userId is not empty
        if (!userId.isNullOrEmpty()) {
            val notificationPrefs = NotificationPreferenceManager(requireContext())
            val notifications = notificationPrefs.getNotifications(userId)

            // Set the adapter for RecyclerView with notifications
            rv.adapter = NotificationAdapter(notifications)
            clearButton.setOnClickListener {
                Log.d("HomeFragment", "Clear button clicked")  // Debugging line
                notificationPrefs.clearNotifications(userId)
                rv.adapter?.notifyDataSetChanged()
                dialog.dismiss()
            }
        } else {
            // Set an empty list if userId is empty
            rv.adapter = NotificationAdapter(emptyList())
        }

        // Create the dialog


        // Ensure the dialog is not adding the same view again
        val window = dialog.window
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        // If dialogView already has a parent, remove it
        val parent = dialogView.parent
        if (parent != null) {
            (parent as ViewGroup).removeView(dialogView)
        }

        // Set fixed height for the dialog window
        val layoutParams = window?.attributes
        layoutParams?.height = 1200  // Set the fixed height here (in pixels)
        layoutParams?.width = LinearLayout.LayoutParams.MATCH_PARENT  // Full width
        window?.attributes = layoutParams

        // Show the dialog
        dialog.setCancelable(true)
        dialog.show()
    }

    private fun observeWalletTotals() {
        planViewModel.getStocksWalletTotal().observe(viewLifecycleOwner) { stockTotal ->
            binding.stocksAmount.text = "$%.2f".format(stockTotal)

        }

        planViewModel.getMedicineWalletTotal().observe(viewLifecycleOwner) { medicineTotal ->
            binding.medicineAmount.text = "$%.2f".format(medicineTotal)
        }

        planViewModel.getForexWalletTotal().observe(viewLifecycleOwner) { forexTotal ->
            binding.forexAmount.text = "$%.2f".format(forexTotal)
        }
    }

    private fun navigateToInvestmentScreen(plan: PlanModel) {
        val planType = plan.type // "Stocks", "Medicine", or "Forex"

        when (planType) {
            "Stocks" -> showBuyDetailsBottomSheet(plan)
            "Medicine" -> showBuyMedicineBottomSheet(plan)
            "Forex" -> showBuyForexBottomSheet(plan)
            else -> throw IllegalArgumentException("Unknown plan type")
        }


    }

    private fun showBuyMedicineBottomSheet(plan: PlanModel) {
        val dialog = BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialog)
        val binding = DialogeBuyMedicineBinding.inflate(layoutInflater)

        dialog.setContentView(binding.root)
        dialog.setCancelable(true)
        dialog.show()

        // Set plan info
        binding.tvTitle.text = plan.planName
        binding.tvSymbol.text = plan.type
        binding.tvPrice.text = "$${plan.minAmount}" // Set fixed amount display (if available)
        binding.tvChange.text = "+%.2f%%".format(plan.dailyPercentage)
        binding.PriceET.setText(plan.minAmount.toString()) // Pre-fill min amount for user


        val buyPlanRepo = BuyPlanRepo(requireContext())

        // Handle Buy click
        binding.btnBuy.setOnClickListener {
            val enteredAmountText = binding.PriceET.text.toString().replace("$", "").trim()


            // Validate if entered amount is a valid number
            if (enteredAmountText.isEmpty() || enteredAmountText.toDoubleOrNull() == null) {
                Toast.makeText(requireContext(), "Please enter a valid amount!", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            val investedAmount = enteredAmountText.toDouble()

            fun toast(msg: String) =
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

            // 1.  Make sure the plan HAS a minimum
            val min = plan.minAmount ?: run {
                toast("Plan data is incomplete. Please try again later.")
                return@setOnClickListener
            }

            // 2.  Validate the user’s input
            when {
                investedAmount <= 0.0 -> {
                    toast("Please enter an amount greater than zero.")
                    return@setOnClickListener
                }

                investedAmount < min -> {
                    toast("Minimum investment amount is $min.")
                    return@setOnClickListener
                }
            }


            Log.d("StockInvestment", "Invested Amount: $investedAmount")

            // Dismiss the dialog first
            dialog.dismiss()
            // Show the loading overlay
            showLoading()

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val status = buyPlanRepo.buyMedicine(
                        investedAmount, // ✅ Auto use amount from plan
                        plan.planName.toString()
                    )

                    when (status) {
                        Status.SUCCESS -> {
                            Toast.makeText(
                                requireContext(), "Plan bought successfully!", Toast.LENGTH_SHORT
                            ).show()
                        }

                        Status.NOT_ENOUGH_BALANCE -> {
                            Toast.makeText(
                                requireContext(), "Insufficient balance!", Toast.LENGTH_SHORT
                            ).show()
                        }

                        Status.INVALID_AMOUNT -> {
                            Toast.makeText(requireContext(), "Invalid amount!", Toast.LENGTH_SHORT)
                                .show()
                        }

                        else -> {
                            Toast.makeText(
                                requireContext(),
                                "Failed to buy plan!",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }
                    }
                } finally {
                    // Hide the loading overlay regardless of outcome
                    hideLoading()
                }
            }
        }

        binding.btnBack.setOnClickListener {
            dialog.dismiss()
        }
    }


    private fun showBuyForexBottomSheet(plan: PlanModel) {
        val dialog = BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialog)
        val binding = DialogeBuyMedicineBinding.inflate(layoutInflater)

        dialog.setContentView(binding.root)
        dialog.setCancelable(true)
        dialog.show()

        // Set plan info
        binding.tvTitle.text = plan.planName
        binding.tvSymbol.text = plan.type
        binding.tvPrice.text = "$${plan.minAmount}" // Set fixed amount display (if available)
        // Option 1: Kotlin string-format
        binding.tvChange.text = "+%.2f%%".format(plan.dailyPercentage)

        binding.PriceET.setText(plan.minAmount.toString()) // Pre-fill min amount for user


        val buyPlanRepo = BuyPlanRepo(requireContext())

        // Handle Buy click
        binding.btnBuy.setOnClickListener {
            val enteredAmountText = binding.PriceET.text.toString().replace("$", "").trim()


            // Validate if entered amount is a valid number
            if (enteredAmountText.isEmpty() || enteredAmountText.toDoubleOrNull() == null) {
                Toast.makeText(requireContext(), "Please enter a valid amount!", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            val investedAmount = enteredAmountText.toDouble()

            fun toast(msg: String) =
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

            // 1.  Make sure the plan HAS a minimum
            val min = plan.minAmount ?: run {
                toast("Plan data is incomplete. Please try again later.")
                return@setOnClickListener
            }

            // 2.  Validate the user’s input
            when {
                investedAmount <= 0.0 -> {
                    toast("Please enter an amount greater than zero.")
                    return@setOnClickListener
                }

                investedAmount < min -> {
                    toast("Minimum investment amount is $min.")
                    return@setOnClickListener
                }
            }


            Log.d("StockInvestment", "Invested Amount: $investedAmount")

            // Dismiss the dialog first
            dialog.dismiss()
            // Show the loading overlay
            showLoading()

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val status = buyPlanRepo.buyPlan(
                        investedAmount, // ✅ Auto use amount from plan
                        planName = plan.planName.toString()
                    )

                    when (status) {
                        Status.SUCCESS -> {
                            Toast.makeText(
                                requireContext(), "Plan bought successfully!", Toast.LENGTH_SHORT
                            ).show()
                        }

                        Status.NOT_ENOUGH_BALANCE -> {
                            Toast.makeText(
                                requireContext(), "Insufficient balance!", Toast.LENGTH_SHORT
                            ).show()
                        }

                        Status.INVALID_AMOUNT -> {
                            Toast.makeText(requireContext(), "Invalid amount!", Toast.LENGTH_SHORT)
                                .show()
                        }

                        else -> {
                            Toast.makeText(
                                requireContext(),
                                "Failed to buy plan!",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }
                    }
                } finally {
                    // Hide the loading overlay regardless of outcome
                    hideLoading()
                }
            }
        }

        binding.btnBack.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun showBuyDetailsBottomSheet(plan: PlanModel) {
        val secondBottomSheet = BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialog)
        val binding = DialogueBuyStockBinding.inflate(layoutInflater)
        secondBottomSheet.setContentView(binding.root)
        secondBottomSheet.setCancelable(true)
        secondBottomSheet.show()

        binding.tvTitle.text = plan.planName
        binding.tvSymbol.text = plan.type
        binding.PriceET.setText("${plan.minAmount}") // Pre-fill min amount for user
        binding.amountValueTV.text = "${plan.minAmount}"

        binding.btnBack.setOnClickListener {
            secondBottomSheet.dismiss()
        }

        binding.btnBuy.setOnClickListener {
            val enteredAmountText = binding.PriceET.text.toString().replace("$", "").trim()
            // Pre-fill min amount for user

            // Validate if entered amount is a valid number
            if (enteredAmountText.isEmpty() || enteredAmountText.toDoubleOrNull() == null) {
                Toast.makeText(requireContext(), "Please enter a valid amount!", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            val investedAmount = enteredAmountText.toDouble()

            fun toast(msg: String) =
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

            // 1.  Make sure the plan HAS a minimum
            val min = plan.minAmount ?: run {
                toast("Plan data is incomplete. Please try again later.")
                return@setOnClickListener
            }

            // 2.  Validate the user’s input
            when {
                investedAmount <= 0.0 -> {
                    toast("Please enter an amount greater than zero.")
                    return@setOnClickListener
                }

                investedAmount < min -> {
                    toast("Minimum investment amount is $min.")
                    return@setOnClickListener
                }
            }


            Log.d("StockInvestment", "Invested Amount: $investedAmount")

            // Dismiss the dialog before showing the loading overlay
            secondBottomSheet.dismiss()

            // Show the loading overlay using BaseFragment method
            showLoading()

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    Log.d(
                        "StockInvestment",
                        "Calling buyPlan with amount: $investedAmount and plan: ${plan.planName}"
                    )

                    val status = repo.buyStock(
                        amount = investedAmount, stockSymbol = plan.planName.toString()
                    )

                    Log.d("StockInvestment", "Buy Plan status: $status")

                    when (status) {
                        Status.SUCCESS -> {
                            Toast.makeText(
                                requireContext(), "Plan purchased successfully!", Toast.LENGTH_SHORT
                            ).show()
                        }

                        Status.NOT_ENOUGH_BALANCE -> {
                            Toast.makeText(requireContext(), "Low Balance!", Toast.LENGTH_SHORT)
                                .show()
                        }

                        Status.INVALID_AMOUNT -> {
                            Toast.makeText(
                                requireContext(), "Invalid investment amount!", Toast.LENGTH_SHORT
                            ).show()
                        }

                        else -> {
                            Toast.makeText(
                                requireContext(),
                                "Something went wrong! Status: $status",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } finally {
                    // Hide the loading overlay regardless of the outcome
                    hideLoading()
                }
            }
        }
    }

    private fun generateHmac(data: String, key: String): String {
        val hmacSha512 = "HmacSHA512"
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), hmacSha512)
        val mac = Mac.getInstance(hmacSha512)
        mac.init(secretKey)
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") {
            "%02x".format(it)
        }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Bail out if the view is already gone
        val bind = _binding ?: return

        val lmForex = bind.forexRecycler.layoutManager as? LinearLayoutManager
        val lmStocks = bind.topStocksRecycler.layoutManager as? LinearLayoutManager
        val lmMedicines = bind.topMedicinesRecycler.layoutManager as? LinearLayoutManager

        outState.putInt("scroll_forex", lmForex?.findFirstVisibleItemPosition() ?: 0)
        outState.putInt("scroll_stocks", lmStocks?.findFirstVisibleItemPosition() ?: 0)
        outState.putInt("scroll_medicines", lmMedicines?.findFirstVisibleItemPosition() ?: 0)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState ?: return       // nothing to do
        _binding ?: return                  // view might already be gone

        val lmForex = binding.forexRecycler.layoutManager as? LinearLayoutManager
        val lmStocks = binding.topStocksRecycler.layoutManager as? LinearLayoutManager
        val lmMedicines = binding.topMedicinesRecycler.layoutManager as? LinearLayoutManager

        lmForex?.scrollToPositionWithOffset(savedInstanceState.getInt("scroll_forex"), 0)
        lmStocks?.scrollToPositionWithOffset(savedInstanceState.getInt("scroll_stocks"), 0)
        lmMedicines?.scrollToPositionWithOffset(savedInstanceState.getInt("scroll_medicines"), 0)
    }
}