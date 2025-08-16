package com.pse.pse.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import com.google.firebase.Timestamp
import com.pse.pse.R
import com.pse.pse.databinding.FragmentSignUpBinding
import com.pse.pse.models.UserModel
import com.pse.pse.ui.viewModels.AuthViewModel
import com.pse.pse.utils.SharedPrefManager

class SignUpFragment : BaseFragment() {

    private lateinit var binding: FragmentSignUpBinding
    private lateinit var authViewModel: AuthViewModel

    // Referral constraints
    companion object {
        private const val REF_PREFIX = "PSE-"
        private const val UID_LEN = 6
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }

    private var editingReferral = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentSignUpBinding.inflate(inflater, container, false)
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupReferralField()

        binding.signUpBtn.setOnClickListener {
            val firstName = binding.firstNameInput.text?.toString()?.trim().orEmpty()
            val lastName = binding.lastNameInput.text?.toString()?.trim().orEmpty()
            val email = binding.emailInput.text?.toString()?.trim().orEmpty()
            val password = binding.passwordInput.text?.toString().orEmpty()
            val phoneNumber = binding.phoneInput.text?.toString()?.trim().orEmpty()
            val referralRaw = binding.referralInput.text?.toString()?.trim().orEmpty().uppercase()

            // ——— Existing validations (unchanged) ———
            if (firstName.isEmpty()) {
                binding.firstNameInput.error = "First name is required"; return@setOnClickListener
            }
            if (lastName.isEmpty()) {
                binding.lastNameInput.error = "Last name is required"; return@setOnClickListener
            }
            if (email.isEmpty()) {
                binding.emailInput.error = "Email is required"; return@setOnClickListener
            }
            if (password.isEmpty()) {
                binding.passwordInput.error = "Password is required"; return@setOnClickListener
            }
            if (phoneNumber.isEmpty()) {
                binding.phoneInput.error = "Phone number is required"; return@setOnClickListener
            }
            if (password.length < 6) {
                binding.passwordInput.error =
                    "Password must be at least 6 characters"; return@setOnClickListener
            }

            // ——— Referral validation ———
            val body = referralRaw.removePrefix(REF_PREFIX)
            // If user typed something after PSE-, it must be exactly 6 chars and exist.
            // If they left only "PSE-" (no body), treat as empty/optional (keeps old behavior).
            if (body.isNotEmpty() && body.length != UID_LEN) {
                binding.referralInput.error =
                    "Referral must be ${REF_PREFIX}${UID_LEN} chars (e.g. PSE-AB3D7K)"
                return@setOnClickListener
            }

            fun proceedRegister(resolvedReferral: String) {
                val userModel = UserModel(
                    uid = "",
                    name = firstName,
                    lastName = lastName,
                    email = email,
                    password = password,
                    phoneNumber = phoneNumber,
                    referralCode = resolvedReferral, // full "PSE-XXXXXX" or "" if not provided
                    deviceToken = "",
                    createdAt = Timestamp.now(),
                    isBlocked = false,
                    status = "inactive"
                )

                showLoading()
                authViewModel.registerUser(userModel) { firebaseUser ->
                    hideLoading()
                    if (firebaseUser == null) {
                        Toast.makeText(requireContext(), "Registration failed", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        val sharedPref = SharedPrefManager(requireContext())
                        sharedPref.saveUserName("$firstName $lastName")
                        sharedPref.saveUserEmail(email)

                        view.findNavController().navigate(R.id.action_signUp_to_signIn)
                        Toast.makeText(
                            requireContext(),
                            "Registration successful. Please verify the email we sent to ${userModel.email}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            // If body empty → optional, skip existence check.
            if (body.isEmpty()) {
                proceedRegister("") // keep old logic (referral optional)
                return@setOnClickListener
            }

            // Check existence via ViewModel before proceeding
            showLoading()
            authViewModel.checkReferrer(REF_PREFIX + body) { exists ->
                hideLoading()
                if (!exists) {
                    binding.referralInput.error = "No user exists with this ID"
                } else {
                    proceedRegister(REF_PREFIX + body)
                }
            }
        }
    }

    private fun setupReferralField() {
        val et = binding.referralInput

        // If a deep link saved referral exists, normalize it; else prefill "PSE-"
        val savedRef = SharedPrefManager(requireContext()).getReferralFromLink()
        val startValue = when {
            savedRef.isNullOrBlank() -> REF_PREFIX
            savedRef.startsWith(REF_PREFIX, ignoreCase = true) -> savedRef.uppercase()
            else -> (REF_PREFIX + savedRef).uppercase()
        }
        et.setText(startValue)
        et.setSelection(et.text?.length ?: REF_PREFIX.length)

        // Keep prefix intact, force CAPS, allow only ALPHABET, cap body to UID_LEN
        et.addTextChangedListener(
            onTextChanged = { s, _, _, _ ->
                if (editingReferral) return@addTextChangedListener
                editingReferral = true

                val raw = (s?.toString() ?: "").uppercase()

                // Ensure prefix is present (and not deletable)
                var body = raw.removePrefix(REF_PREFIX)

                // Keep only allowed chars in the body
                body = body.filter { ALPHABET.contains(it) }

                // Limit to UID_LEN
                if (body.length > UID_LEN) body = body.substring(0, UID_LEN)

                val fixed = REF_PREFIX + body
                if (fixed != raw) {
                    et.setText(fixed)
                    et.setSelection(fixed.length)
                }

                // Clear error as user edits
                binding.referralInput.error = null

                editingReferral = false
            }
        )
    }
}