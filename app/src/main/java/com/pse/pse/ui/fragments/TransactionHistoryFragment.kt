package com.pse.pse.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.pse.pse.R
import com.pse.pse.databinding.FragmentTransactionHistoryBinding
import com.pse.pse.adapters.NotificationAdapter
import com.trustledger.aitrustledger.adapters.TransactionAdapter
import com.pse.pse.models.TransactionModel
import com.pse.pse.ui.viewModels.TransactionViewModel
import com.pse.pse.utils.NotificationPreferenceManager
import com.pse.pse.utils.SharedPrefManager


class TransactionHistoryFragment : BaseFragment() {

    private var _binding: FragmentTransactionHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TransactionViewModel by viewModels()

    // All transactions loaded from Firestore
    private var allTransactions: List<TransactionModel> = emptyList()

    // Currently applied transaction type filter (null = all types)
    private var typeFilter: String? = null
    private lateinit var adapter: TransactionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        // Initialize RecyclerView
        adapter = TransactionAdapter()
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter
        binding.notificationIcon.setOnClickListener {
            showNotificationsDialog()
        }
        // Observe full history and apply filters
        viewModel.transactionHistory.observe(viewLifecycleOwner) { history ->
            allTransactions = history
            applyFilters(
                selectedTab = binding.tabStatus.selectedTabPosition, typeFilter = typeFilter
            )
        }

        // Load transaction history
        viewModel.loadTransactionHistory()

        // Tab selection: filter by status
        binding.tabStatus.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                applyFilters(tab.position, typeFilter)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        // Filter icon click: show type filter dialog
        binding.ivFilter.setOnClickListener {
            showTypeFilterDialog()
        }

        typeFilter = TransactionModel.TYPE_DEPOSIT
    }

    /**
     * Applies both status (tab) and type filters, then updates the adapter.
     */
    private fun applyFilters(selectedTab: Int, typeFilter: String?) {

        binding.tabLayoutContainer.visibility = when (typeFilter) {
            TransactionModel.TYPE_DEPOSIT, TransactionModel.TYPE_WITHDRAW -> View.VISIBLE
            else -> View.GONE
        }


        val statusFiltered = when (typeFilter) {
            TransactionModel.TYPE_DEPOSIT, TransactionModel.TYPE_WITHDRAW -> {
                when (selectedTab) {
                    1 -> allTransactions.filter { it.status == TransactionModel.STATUS_APPROVED }
                    2 -> allTransactions.filter { it.status == TransactionModel.STATUS_REJECTED }
                    else -> {
                        val pending =
                            allTransactions.filter { it.status == TransactionModel.STATUS_PENDING }
                        val approved =
                            allTransactions.filter { it.status == TransactionModel.STATUS_APPROVED }
                        val rejected =
                            allTransactions.filter { it.status == TransactionModel.STATUS_REJECTED }
                        pending + approved + rejected
                    }
                }
            }

            else -> {
                // Other types (achievement, teamReward, etc.) — ignore tabs, show all
                allTransactions
            }
        }

        // Further filter by type if set
        val typeFilteredList = typeFilter?.let { filterType ->
            statusFiltered.filter { it.type == filterType }
        } ?: statusFiltered

//        val typeFilteredList = statusFiltered.filter { it.type == typeFilter }

        val timeSorted = if (typeFilter == TransactionModel.TYPE_ACHIEVEMENT) {
            typeFilteredList.sortedBy { it.timestamp?.seconds ?: 0 } // ascending
        } else {
            typeFilteredList.sortedByDescending { it.timestamp?.seconds ?: 0 } // default
        }
        adapter.setData(timeSorted)

    }

    /**
     * Shows a single-choice dialog to select transaction type filter.
     */
    private fun showTypeFilterDialog() {
        val types = arrayOf(
            "Deposit History",
            "Withdraw History",
            "Team Rewards History",
            "Achievement History",
            "Bought Investment History",
            "Sold Investment History",
            "Referral History"
        )

        val currentIndex = when (typeFilter) {
            TransactionModel.TYPE_DEPOSIT -> 0
            TransactionModel.TYPE_WITHDRAW -> 1
            TransactionModel.TYPE_TEAM -> 2
            TransactionModel.TYPE_ACHIEVEMENT -> 3
            TransactionModel.TYPE_INVESTMENT_BOUGHT -> 4
            TransactionModel.TYPE_INVESTMENT_SOLD -> 5
            TransactionModel.TYPE_REFERRAL -> 6
            else -> 0
        }

        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.material_dialog, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.dialogContainer)

        val radioGroup = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered
        )
            .setView(dialogView)
            .create() // no .setNegativeButton()

        types.forEachIndexed { index, label ->
            val radioButton = RadioButton(requireContext()).apply {
                text = label
                id = View.generateViewId()
                isChecked = index == currentIndex
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                buttonTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.dark_green))
                textSize = 18f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 46, 24, 46)

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 16
                    bottomMargin = 16
                    gravity = Gravity.CENTER
                }
            }

            radioButton.setOnClickListener {
                typeFilter = when (index) {
                    0 -> TransactionModel.TYPE_DEPOSIT
                    1 -> TransactionModel.TYPE_WITHDRAW
                    2 -> TransactionModel.TYPE_TEAM
                    3 -> TransactionModel.TYPE_ACHIEVEMENT
                    4 -> TransactionModel.TYPE_INVESTMENT_BOUGHT
                    5 -> TransactionModel.TYPE_INVESTMENT_SOLD
                    6 -> TransactionModel.TYPE_REFERRAL
                    else -> TransactionModel.TYPE_DEPOSIT
                }

                applyFilters(
                    selectedTab = binding.tabStatus.selectedTabPosition,
                    typeFilter = typeFilter
                )
                dialog.dismiss()
            }

            radioGroup.addView(radioButton)
        }

        container.addView(radioGroup)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
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
        val userId = SharedPrefManager(requireContext()).getId()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
