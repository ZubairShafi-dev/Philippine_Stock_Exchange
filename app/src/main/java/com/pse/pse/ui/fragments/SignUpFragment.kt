package com.pse.pse.ui.fragments

import UserModel
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import com.google.firebase.Timestamp
import com.pse.pse.R
import com.pse.pse.databinding.FragmentSignUpBinding
import com.pse.pse.ui.viewModels.AuthViewModel
import com.pse.pse.utils.SharedPrefManager

class SignUpFragment : BaseFragment() {

    private lateinit var binding: FragmentSignUpBinding
    private lateinit var authViewModel: AuthViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSignUpBinding.inflate(inflater, container, false)
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ─── Pre-fill referral field from SharedPref (if any) ────────────────
        SharedPrefManager(requireContext()).getReferralFromLink()?.let { savedRef ->
            binding.referralInput.setText(savedRef)
        }
        binding.signUpBtn.setOnClickListener {
            val firstName = binding.firstNameInput.text.toString()
            val lastName = binding.lastNameInput.text.toString()
            val email = binding.emailInput.text.toString()
            val password = binding.passwordInput.text.toString()
            val phoneNumber = binding.phoneInput.text.toString()
            val referralCode = binding.referralInput.text.toString()

            if (firstName.isEmpty()) {
                binding.firstNameInput.error = "First name is required"
                return@setOnClickListener
            }
            if (lastName.isEmpty()) {
                binding.lastNameInput.error = "Last name is required"
                return@setOnClickListener
            }
            if (email.isEmpty()) {
                binding.emailInput.error = "Email is required"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                binding.passwordInput.error = "Password is required"
                return@setOnClickListener
            }
            if (phoneNumber.isEmpty()) {
                binding.phoneInput.error = "Phone number is required"
                return@setOnClickListener
            }
            if (password.length < 6) {
                binding.passwordInput.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }

            val userModel = UserModel(
                uid = "",
                name = firstName,
                lastName = lastName,
                email = email,
                password = password,
                phoneNumber = phoneNumber,
                referralCode = referralCode,
                deviceToken = "",
                createdAt = Timestamp.now(),
                isBlocked = false,
                status = "inactive"
            )

            showLoading()
            authViewModel.registerUser(userModel) { firebaseUser ->
                hideLoading()
                if (firebaseUser == null) {
                    Toast.makeText(requireContext(), "Registration failed", Toast.LENGTH_SHORT).show()
                } else {
                    val sharedPref = SharedPrefManager(requireContext())
                    sharedPref.saveUserName("$firstName $lastName")
                    sharedPref.saveUserEmail(email)

                    view.findNavController().navigate(R.id.action_signUp_to_signIn)  // Navigate to login
                    Toast.makeText(requireContext(), "Registration successful Please verify your email" +
                            "we sent to ${userModel.email}", Toast.LENGTH_LONG).show()

                }
            }
        }
    }
}
