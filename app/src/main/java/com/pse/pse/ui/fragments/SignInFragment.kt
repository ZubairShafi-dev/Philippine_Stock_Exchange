package com.pse.pse.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.pse.pse.R
import com.pse.pse.databinding.FragmentSignInBinding
import com.pse.pse.viewModels.AuthViewModel

class SignInFragment : BaseFragment() {

    private lateinit var binding: FragmentSignInBinding
    private lateinit var authViewModel: AuthViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSignInBinding.inflate(inflater, container, false)
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginBtn.setOnClickListener {
            val email = binding.emailInput.text.toString()
            val password = binding.passwordInput.text.toString()
            showLoading()
            authViewModel.loginUser(email, password) { isUserLoggedIn, firebaseUser ->
                hideLoading()
                if (isUserLoggedIn) {
                    view.findNavController().navigate(R.id.action_signInFragment_to_homeFragment)
                } else {
                    if (firebaseUser != null && !firebaseUser.isEmailVerified) {
                        showEmailSentDialog(firebaseUser)  // Show email verification dialog here
                    } else {
                        Toast.makeText(requireContext(), "Login Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.signUpLink.setOnClickListener {
            view.findNavController().navigate(R.id.action_signInFragment_to_signUpFragment)
        }

        binding.forgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun showForgotPasswordDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.forgot_password_dialoge)
        dialog.setCancelable(true)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
        }
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val emailInput = dialog.findViewById<EditText>(R.id.emailInput)
        val btnSend = dialog.findViewById<Button>(R.id.resetBtn)

        btnSend?.setOnClickListener {
            val email = emailInput?.text.toString().trim()
            if (email.isEmpty()) {
                emailInput?.error = "Enter your email"
                return@setOnClickListener
            }

            authViewModel.sendPasswordReset(email) { success ->
                dialog.dismiss()
                val msg = if (success) "Reset link sent to $email" else "Failed to send reset link"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    private fun showEmailSentDialog(user: FirebaseUser) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.email_verification_dialoge)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Verify Button
        val btnVerified = dialog.findViewById<Button>(R.id.verifyButton)
        btnVerified.setOnClickListener {
            user.reload().addOnCompleteListener {
                if (it.isSuccessful) {
                    // Always get the latest user from FirebaseAuth
                    val refreshedUser = FirebaseAuth.getInstance().currentUser
                    if (refreshedUser != null && refreshedUser.isEmailVerified) {
                        dialog.dismiss()
                        Toast.makeText(requireContext(), "Email Verified Successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Email not verified yet!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Failed to reload user data.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Resend Button
        val btnResend = dialog.findViewById<Button>(R.id.resendButton)  // Assuming you added this button in the XML
        btnResend.setOnClickListener {
            // Check if email is already verified
            if (user.isEmailVerified) {
                Toast.makeText(requireContext(), "Email is already verified.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Resend verification email
            user.sendEmailVerification().addOnCompleteListener { resendTask ->
                if (resendTask.isSuccessful) {
                    Toast.makeText(requireContext(), "Verification email sent to ${user.email}", Toast.LENGTH_SHORT).show()
                    user.reload().addOnCompleteListener { reloadTask ->
                        val refreshedUser = FirebaseAuth.getInstance().currentUser
                        if (reloadTask.isSuccessful) {
                            if (refreshedUser != null && refreshedUser.isEmailVerified) {
                                Toast.makeText(requireContext(), "Email Verified Successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(requireContext(), "Email still not verified. Please check your inbox.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(requireContext(), "Failed to reload user data.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // Display failure message based on the exception
                    val errorMessage = resendTask.exception?.message ?: "Unknown error"
                    Toast.makeText(requireContext(), "Failed to send email: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }
}
