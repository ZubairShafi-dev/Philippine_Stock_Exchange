package com.pse.pse.ui.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.pse.pse.R
import com.pse.pse.adapters.AnnouncementAdapter
import com.pse.pse.adapters.AnnouncementSliderAdapter
import com.pse.pse.adapters.NotificationAdapter
import com.pse.pse.databinding.FragmentHomeBinding
import com.pse.pse.models.AnnouncementModel
import com.pse.pse.ui.viewModels.AccountViewModel
import com.pse.pse.utils.NotificationPreferenceManager
import com.pse.pse.utils.SharedPrefManager
import java.text.DecimalFormat
import kotlin.math.roundToInt

class HomeFragment : BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var accountViewModel: AccountViewModel
    private lateinit var sliderAdapter: AnnouncementSliderAdapter
    private val sliderHandler = Handler(Looper.getMainLooper())
    private var sliderRunnable: Runnable? = null

    private val moneyFmt = DecimalFormat("#,##0.00")
    private lateinit var sharedPref: SharedPrefManager
    private var isDialogShowing = false

    companion object {
        private const val TAG_PROFILE = "HomeFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        sharedPref = SharedPrefManager(requireContext())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        accountViewModel = ViewModelProvider(this)[AccountViewModel::class.java]
        val userId = SharedPrefManager(requireContext()).getId()

        setupClickListeners()
        observeAccountData(userId)
        setupAnnouncementSlider()
        fetchAnnouncements()
    }

    /** Set click listeners for navigation */
    private fun setupClickListeners() {
        binding.depositAmount.root.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_depositAmount)
        }
        binding.withdrawAmount.root.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_withdrawAmount)
        }
        binding.boughtPlans.root.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_planFragment)
        }
        binding.notificationIcon.setOnClickListener {
            showNotificationsDialog()
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

        // Notification & Announcement icons can be wired later if needed
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
        Log.d(TAG_PROFILE, "showNotificationsDialog called")

        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialoge_notification, null)
        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered
        ).setView(dialogView).create()

        val userId = sharedPref.getId()
        val rv = dialogView.findViewById<RecyclerView>(R.id.notificationRv)
        val clearButton = dialogView.findViewById<TextView>(R.id.clearNotificationView)
        rv.layoutManager = LinearLayoutManager(requireContext())

        if (!userId.isNullOrEmpty()) {
            val notificationPrefs = NotificationPreferenceManager(requireContext())
            val notifications = notificationPrefs.getNotifications(userId)
            rv.adapter = NotificationAdapter(notifications)

            clearButton.setOnClickListener {
                notificationPrefs.clearNotifications(userId)
                rv.adapter?.notifyDataSetChanged()
                dialog.dismiss()
            }
        } else {
            rv.adapter = NotificationAdapter(emptyList())
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(true)
        dialog.show()
    }


    /** Observe account data and update UI */
    private fun observeAccountData(userId: String?) {
        showLoading()
        accountViewModel.getAccount(userId).observe(viewLifecycleOwner) { account ->
            hideLoading()
            if (account == null) return@observe

            val inv = account.investment
            val earn = account.earnings

            fun rs(v: Double) = "Rs. ${moneyFmt.format(v)}"

            // Top bar
            binding.profileTitle.text = "My Wallet"

            // Wallet card
            binding.walletCard.userName.text = "ID: ${account.accountId}"
            binding.walletCard.tvAmount.text = rs(inv.currentBalance)

            // Earnings cards
            binding.earningTodayCard.earningTitle.text = "Today's Earning"
            binding.earningTodayCard.earningAmount.text = rs(earn.dailyProfit)

            binding.earningTotalCard.earningTitle.text = "Total Earned"
            binding.earningTotalCard.earningAmount.text = rs(earn.totalEarned)

            binding.earningReferralCard.earningTitle.text = "Referral Profit"
            binding.earningReferralCard.earningAmount.text = rs(earn.referralProfit)

            binding.earningTeamCard.earningTitle.text = "Team Profit"
            binding.earningTeamCard.earningAmount.text = rs(earn.teamProfit)

            // Optional progress mapping: part / totalEarned
            setEarningProgress(earn.dailyProfit, earn.referralProfit, earn.teamProfit, earn.totalEarned)
        }
    }

    private fun setEarningProgress(today: Double, referral: Double, team: Double, total: Double) {
        val denom = if (total <= 0.0) 1.0 else total

        fun pct(part: Double): Int {
            val p = (part / denom) * 100.0
            return p.coerceIn(0.0, 100.0).roundToInt()
        }

        binding.earningTodayCard.earningProgress.progress = pct(today)
        binding.earningReferralCard.earningProgress.progress = pct(referral)
        binding.earningTeamCard.earningProgress.progress = pct(team)
        binding.earningTotalCard.earningProgress.progress = 100
    }

    /** Setup slider adapter and auto-scroll */
    private fun setupAnnouncementSlider() {
        sliderAdapter = AnnouncementSliderAdapter(mutableListOf())
        binding.announcementSlider.adapter = sliderAdapter

        TabLayoutMediator(binding.sliderDots, binding.announcementSlider) { _, _ -> }.attach()

        sliderRunnable = Runnable {
            val currentItem = binding.announcementSlider.currentItem
            val itemCount = sliderAdapter.itemCount
            if (itemCount > 0) {
                binding.announcementSlider.currentItem = (currentItem + 1) % itemCount
            }
        }

        binding.announcementSlider.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                sliderRunnable?.let {
                    sliderHandler.removeCallbacks(it)
                    sliderHandler.postDelayed(it, 3000) // 3 seconds
                }
            }
        })
    }

    /** Fetch announcements from ViewModel */
    private fun fetchAnnouncements() {
        accountViewModel.getAnnouncementImageUrls()
        accountViewModel.announcementImageUrls.observe(viewLifecycleOwner) { urls ->
            urls?.let {
                sliderAdapter.updateData(it)
                if (it.isNotEmpty()) {
                    sliderRunnable?.let { r -> sliderHandler.postDelayed(r, 3000) }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sliderRunnable?.let { sliderHandler.removeCallbacks(it) }
        _binding = null
    }
}
