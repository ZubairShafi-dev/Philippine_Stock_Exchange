package com.pse.pse.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pse.pse.R
import com.pse.pse.utils.SharedPrefManager
import com.pse.pse.adapters.NotificationAdapter
import com.pse.pse.data.repository.BuyPlanRepo
import com.pse.pse.databinding.FragmentPlansBinding
import com.pse.pse.ui.viewModels.PlanViewModel
import com.pse.pse.ui.viewModels.PlanViewModelFactory
import com.pse.pse.utils.NotificationPreferenceManager

class PlansFragment : BaseFragment() {

    private lateinit var planViewModel: PlanViewModel
    private lateinit var binding: FragmentPlansBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val repository = BuyPlanRepo(requireContext())
        val factory = PlanViewModelFactory(repository)
        planViewModel = ViewModelProvider(this, factory).get(PlanViewModel::class.java)
        binding = FragmentPlansBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        val repository = BuyPlanRepo(requireContext())
        val factory = PlanViewModelFactory(repository)
        planViewModel = ViewModelProvider(this, factory).get(PlanViewModel::class.java)

        val cardStocks: CardView = view.findViewById(R.id.itemCardStockInvestment)
        val cardMedicine: CardView = view.findViewById(R.id.item_card_medicine_investment)
        val cardForex: CardView = view.findViewById(R.id.item_card_forex_investment)

        val userId = SharedPrefManager(requireContext()).getId().toString()
        planViewModel.initUserId(userId)
        observeWalletTotals()
        cardStocks.setOnClickListener {
            view.findNavController()
                .navigate(R.id.action_plansFragment_to_stockInvestmentPlansFragment)

        }
        binding.btnInvestStocks.setOnClickListener {
            view.findNavController()
                .navigate(R.id.action_plansFragment_to_stockInvestmentPlansFragment)
        }
        cardMedicine.setOnClickListener {
            view.findNavController()
                .navigate(R.id.action_plansFragment_to_medicineInvestmenetPlanFragment)
        }
        binding.btnInvestMedicine.setOnClickListener {
            view.findNavController()
                .navigate(R.id.action_plansFragment_to_medicineInvestmenetPlanFragment)
        }
        cardForex.setOnClickListener {
            view.findNavController()
                .navigate(R.id.action_plansFragment_to_forexInvestmentFragment)
        }
        binding.btnInvestForex.setOnClickListener {
            view.findNavController()
                .navigate(R.id.action_plansFragment_to_forexInvestmentFragment)
        }
        binding.notificationIcon.setOnClickListener {
            showNotificationsDialog()
        }
    }

    private fun observeWalletTotals() {
        // Observe the total wallet for Stocks
        planViewModel.getStocksWalletTotal().observe(viewLifecycleOwner, Observer { stockTotal ->
            // Update UI with the fetched total for stocks
            Log.d("StockTotal", stockTotal.toString())
            binding.tvAmount.text = "$%.2f".format(stockTotal)
        })

        // Observe the total wallet for Medicine
        planViewModel.getMedicineWalletTotal()
            .observe(viewLifecycleOwner, Observer { medicineTotal ->
                // Update UI with the fetched total for medicine
                Log.d("MedicineTotal", medicineTotal.toString())
                binding.tvAmount2.text = "$%.2f".format(medicineTotal)
            })

        // Observe the total wallet for Forex
        planViewModel.getForexWalletTotal().observe(viewLifecycleOwner, Observer { forexTotal ->
            // Update UI with the fetched total for forex
            Log.d("ForexTotal", forexTotal.toString())
            binding.tvAmount3.text ="$%.2f".format(forexTotal)
        })
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
}
