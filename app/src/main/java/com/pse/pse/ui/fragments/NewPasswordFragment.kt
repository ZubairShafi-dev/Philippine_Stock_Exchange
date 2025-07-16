package com.pse.pse.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.pse.pse.R
import com.pse.pse.data.repository.AuthRepository
import com.pse.pse.databinding.FragmentNewPasswordBinding
import com.pse.pse.utils.SharedPrefManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChangePasswordBottomSheet : BottomSheetDialogFragment() {
    private var _binding: FragmentNewPasswordBinding? = null
    private val binding get() = _binding!!

    // Loading overlay variables and functions (copied from BaseFragment)
    private var loadingOverlay: View? = null

    private lateinit var sharedPref: SharedPrefManager
    private lateinit var authRepo: AuthRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPref = SharedPrefManager(requireContext())
        authRepo = AuthRepository(requireActivity().application)

        // Setup the loading overlay similar to BaseFragment
        var containerView: ViewGroup = view as? ViewGroup ?: return
        if (view is ScrollView && view.childCount == 1) {
            val originalChild = view.getChildAt(0)
            view.removeView(originalChild)
            val frameLayout = FrameLayout(requireContext())
            frameLayout.layoutParams = originalChild.layoutParams
            frameLayout.addView(originalChild)
            view.addView(frameLayout)
            containerView = frameLayout
        }
        loadingOverlay =
            LayoutInflater.from(context).inflate(R.layout.loading_overlay, containerView, false)
        containerView.addView(loadingOverlay)


        binding.updatePasswordBtn.setOnClickListener {
            val oldPass = binding.oldPasswordInput.text.toString().trim()
            val newPass = binding.newPasswordInput.text.toString().trim()
            val confirm = binding.confirmPasswordInput.text.toString().trim()

            if (newPass != confirm) {
                Toast.makeText(context, "❌ Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPass.length < 6) {
                Toast.makeText(
                    context, "Password must be at least 6 characters", Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            showLoading()
            viewLifecycleOwner.lifecycleScope.launch {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (user == null || user.email.isNullOrEmpty()) {
                    hideLoading()
                    Toast.makeText(context, "❌ User not logged in", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                try {
                    // Step 1: Reauthenticate
                    val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(
                        user.email!!, oldPass
                    )
                    user.reauthenticate(credential).await()

                    // Step 2: Update password in FirebaseAuth
                    user.updatePassword(newPass).await()

                    // Step 3: Update password in Firestore (optional)
                    val docId = sharedPref.getDocId()
                    if (!docId.isNullOrEmpty()) {
                        authRepo.updateUserPasswordByDocId(docId, newPass)
                    }

                    // Step 4: Update shared preferences
                    sharedPref.saveUserPassword(newPass)

                    Toast.makeText(context, "✅ Password successfully updated", Toast.LENGTH_SHORT)
                        .show()
                    dismiss()

                } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                    Toast.makeText(context, "❌ Incorrect current password", Toast.LENGTH_SHORT)
                        .show()
                } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                    Toast.makeText(
                        context, "⚠ Please log in again to change your password", Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e("ChangePassword", "❌ Unexpected error: ${e.message}", e)
                    Toast.makeText(context, "❌ Failed to update password", Toast.LENGTH_SHORT)
                        .show()
                } finally {
                    hideLoading()
                }
            }
        }
    }

    fun showLoading() {
        Log.d("ChangePasswordSheet", "showLoading called")
        loadingOverlay?.apply {
            visibility = View.VISIBLE
            bringToFront()
            elevation = 100f
            requestLayout()
        }
    }

    fun hideLoading() {
        Log.d("ChangePasswordSheet", "hideLoading called")
        loadingOverlay?.visibility = View.GONE
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}