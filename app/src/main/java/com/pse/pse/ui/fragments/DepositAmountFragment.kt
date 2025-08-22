package com.pse.pse.ui.fragments

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.pse.pse.R
import com.pse.pse.databinding.FragmentDepositAmountBinding
import com.pse.pse.ui.viewModels.TransactionViewModel
import com.pse.pse.utils.SharedPrefManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


class DepositAmountFragment : BaseFragment() {
    private var _binding: FragmentDepositAmountBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TransactionViewModel by viewModels()

    private lateinit var userId: String
    private lateinit var email: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDepositAmountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        // 1) Load the current balance from Firestore
        viewModel.loadCurrentBalance()
        userId= SharedPrefManager(requireContext()).getId().toString()
        email=FirebaseAuth.getInstance().currentUser?.email.toString()
        // 2) Observe it
        viewModel.currentBalance.observe(viewLifecycleOwner) { balance ->
            val formatted = "$${"%,.2f".format(balance)}"
            binding.cardBalance.text = formatted
            binding.activeBalance.text = "Active Balance  $formatted"
        }

//         3) Live‑format your “would be” balance as you type
        binding.amountValue.addTextChangedListener {
            val raw = it.toString().replace("$", "").replace(",", "")
            val amt = raw.toDoubleOrNull() ?: 0.0
            val current = viewModel.currentBalance.value ?: 0.0
            val newBal = current + amt

            val formatted = "$${"%,.2f".format(newBal)}"
            binding.cardBalance.text = formatted
            binding.activeBalance.text = "Active Balance  $formatted"
        }

        // Observe result and show dialogs
        viewModel.depositResult.observe(viewLifecycleOwner) { success ->

            hideLoading()
            if (success) {
                viewModel.loadCurrentBalance()

                val amount =
                    binding.amountValue.text.toString().replace(",", "").toDoubleOrNull() ?: 0.0
                val formattedAmount = "$${"%,.2f".format(amount)}"
                val subtitleText =
                    "$formattedAmount from the Master Card has been Sent Successfully"

                showCustomDialog(
                    layoutId = R.layout.dialog_request_sent, subtitleText = subtitleText
                )

            } else {
                showCustomDialog(R.layout.dialog_request_error, "Something Went Wrong")
            }
        }


        binding.btnDepositRequest.setOnClickListener {
            val amt = binding.amountValue.text.toString().toDoubleOrNull()
            val addr = binding.walletAddress.text.toString()
            if (amt == null || addr.isBlank()) {
                Toast.makeText(requireContext(), "Please enter valid details", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            if (amt < 20) {
                Toast.makeText(
                    requireContext(), "Minimum deposit amount is $20", Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            showLoading()
            Log.d("DepositAmountFragment", "$amt, email : $email, userId : $userId")
            createDepositRequest(amt,email,userId)

            // call the api here

        }
    }

    private fun createDepositRequest(amount: Double, email: String, userId: String) {
        showLoading()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("amount", amount.toString())
                    put("currency1", "USDT.BEP20")
                    put("currency2", "USDT.BEP20")
                    put("buyer_email", email)
                    put("custom", userId)
                }

                val request = Request.Builder()
                    .url("https://psedeposit-main.onrender.com/api/create-transaction")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = OkHttpClient().newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    val result = JSONObject(body).getJSONObject("result")
                    val address = result.getString("address")
                    val amountToSend = result.getString("amount")
                    val qrUrl = result.getString("qrcode_url")
                    val txnId = result.getString("txn_id")


                    // ✅ Clean up address
                    val cleanAddress = extractCleanAddress(address)
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        showPaymentDialog(cleanAddress, amountToSend, qrUrl, txnId)
                    }
                } else {
                    showToastOnMain("Error: ${response.code}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    showToastOnMain("Exception: ${e.message}")
                }
            }
        }
    }


    private fun extractCleanAddress(address: String): String {
        if (address.contains("?address=")) {
            val startIndex = address.indexOf("?address=") + 9
            val endIndex = address.indexOf("&", startIndex).takeIf { it > 0 } ?: address.length
            return address.substring(startIndex, endIndex)
        }

        if (address.startsWith("ethereum:") && address.contains("/transfer")) {
            val addressPart = address.split("/transfer").first().removePrefix("ethereum:")
            if (addressPart.matches(Regex("0x[a-fA-F0-9]{40}"))) {
                return addressPart
            }
        }

        if (address.startsWith("ethereum:")) {
            val addressPart = address.removePrefix("ethereum:")
            if (addressPart.matches(Regex("0x[a-fA-F0-9]{40}"))) {
                return addressPart
            }
        }

        if (address.matches(Regex("0x[a-fA-F0-9]{40}"))) {
            return address
        }

        return address
    }

    private suspend fun showToastOnMain(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun generateHmac(data: String, key: String): String {
        val hmacSHA512 = "HmacSHA512"
        val secretKey = SecretKeySpec(key.toByteArray(), hmacSHA512)
        val mac = Mac.getInstance(hmacSHA512)
        mac.init(secretKey)
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
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

    private fun showPaymentDialog(address: String, amount: String, qrUrl: String, txnId: String) {
        val ctx = context ?: return  // Prevent crash if context is null

        val dialog = Dialog(ctx)
        dialog.setContentView(R.layout.dialog_qr_scan)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(true)

        // Set QR Code image
        val ivQRCode = dialog.findViewById<ImageView>(R.id.ivQRCode)
        generateLocalQrCode(address, ivQRCode)

        // Set address
        val tvAddress = dialog.findViewById<TextView>(R.id.sendingAddress)
        tvAddress.text = address

        // Set amount
        val tvAmount = dialog.findViewById<TextView>(R.id.amountVal)
        tvAmount.text = "Amount:$amount"

//        // Optional: Set Transaction ID
//        val tvTxnId = dialog.findViewById<TextView?>(R.id.txnId)
//        tvTxnId?.text = txnId

        // Copy to clipboard
        val copyButton = dialog.findViewById<CardView>(R.id.copyButton)
        copyButton.setOnClickListener {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Payment Address", address)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(ctx, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }


    private fun generateLocalQrCode(address: String, ivQRCode: ImageView) {

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hints = hashMapOf<EncodeHintType, Any>(
                    EncodeHintType.MARGIN to 1,
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
                )

                val bitMatrix =
                    QRCodeWriter().encode(address, BarcodeFormat.QR_CODE, 512, 512, hints)
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)

                for (x in 0 until 512) {
                    for (y in 0 until 512) {
                        bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                }

                withContext(Dispatchers.Main) {
                    ivQRCode.setImageBitmap(bitmap)
                    ivQRCode.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                Log.e(TAG, "QR generation error: ${e.message}", e)
                withContext(Dispatchers.Main) {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}