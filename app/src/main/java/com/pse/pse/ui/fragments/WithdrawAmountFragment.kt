package com.pse.pse.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import com.pse.pse.R
import com.pse.pse.databinding.FragmentWithdrawAmountBinding
import com.pse.pse.ui.viewModels.TransactionViewModel

class WithdrawAmountFragment : BaseFragment() {

    private var _binding: FragmentWithdrawAmountBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TransactionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWithdrawAmountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        // 1) Load the current balance from Firestore
        viewModel.loadCurrentBalance()

        // 2) Observe it
        viewModel.currentBalance.observe(viewLifecycleOwner) { balance ->
            val formatted = "$${"%,.0f".format(balance)}"
            binding.cardBalance.text = formatted
            binding.activeBalance.text = "Active Balance  $formatted"
        }

        // 3) Live‑format your “would be” balance as you type
        binding.amountValue.addTextChangedListener {
            val raw = it.toString().replace("$", "").replace(",", "")
            val amt = raw.toDoubleOrNull() ?: 0.0

            val current = viewModel.currentBalance.value ?: 0.0
            val newBal = current - amt

            // Update balance preview (deduct full amount)
            val formattedBal = "$${"%,.2f".format(newBal)}"
            binding.cardBalance.text = formattedBal
            binding.activeBalance.text = "Active Balance  $formattedBal"

            // Show what the user will actually receive after the fixed $4 fee
            val net = if (amt >= 20.0) amt - 4.0 else 0.0  // respect your $20 min
            val formattedNet = "$${"%,.2f".format(net)}"
            binding.youReceive.text = "You’ll receive $formattedNet  (fee $4.00)"
        }

        val hint = SpannableString("Min. 20").apply {
            setSpan(RelativeSizeSpan(0.6f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.amountValue.hint = hint

        // Observe result and show dialogs
        viewModel.withdrawResult.observe(viewLifecycleOwner) { success ->

            hideLoading()
            if (success) {
                val amount =
                    binding.amountValue.text.toString().replace(",", "").toDoubleOrNull() ?: 0.0
                val formattedAmount = "$${"%,.2f".format(amount)}"
                val subtitleText =
                    "$formattedAmount from the Master Card has been Sent Successfully"

                showCustomDialog(
                    layoutId = R.layout.dialog_request_sent, subtitleText = subtitleText
                )
                viewModel.loadCurrentBalance()

            } else {
                showCustomDialog(R.layout.dialog_request_error, "Something Went Wrong")
            }
        }

        // 6) Send the request
        binding.btnWithdrawRequest.setOnClickListener {
            val amt = binding.amountValue.text.toString().toDoubleOrNull()
            val addr = binding.walletAddress.text.toString()
            if (amt == null || addr.isBlank()) {
                Toast.makeText(requireContext(), "Please enter valid details", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            if (amt > viewModel.currentBalance.value ?: 0.0) {
                Toast.makeText(requireContext(), "Insufficient Balance", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (amt < 20) {
                Toast.makeText(
                    requireContext(), "Minimum withdrawal amount is $20", Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            showLoading()
            viewModel.submitWithdrawal(amt, addr)
        }
    }

    private fun showCustomDialog(layoutId: Int, subtitleText: String) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(layoutId)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setCancelable(true)

        // Set subtitle if the view exists
        val tvSubtitle = dialog.findViewById<TextView>(R.id.tvSubtitle)
        tvSubtitle?.text = subtitleText

        dialog.show()

        // Auto-dismiss after 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing) dialog.dismiss()
        }, 3000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}