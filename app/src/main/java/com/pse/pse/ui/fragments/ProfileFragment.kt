package com.pse.pse.ui.fragments


import UserModel
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.storage.FirebaseStorage
import com.pse.pse.databinding.FragmentProfileBinding
import com.pse.pse.ui.viewModels.ProfileViewModel
import com.pse.pse.utils.NotificationPreferenceManager
import com.pse.pse.utils.SharedPrefManager
import com.trustledger.aitrustledger.R
import com.pse.pse.adapters.NotificationAdapter

class ProfileFragment : BaseFragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
    }
    private lateinit var sharedPref: SharedPrefManager

    private var originalPhoneNumber: String = ""
    private val storage = FirebaseStorage.getInstance()
    private val PICK_IMAGE_REQUEST = 1001
    companion object {
        private const val TAG_PROFILE = "ProfileFragment"
    }



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        sharedPref = SharedPrefManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)
        // Make username & email fields read-only
        listOf(binding.editUsername, binding.editEmail).forEach { et ->
            et.apply {
                isFocusable = false
                isFocusableInTouchMode = false
                isCursorVisible = false
                isLongClickable = false
                isClickable = false
            }
        }
        // Contact field editable
        binding.editContact.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            isCursorVisible = true
            isClickable = true
        }

        // Button initial visibility
        binding.updatePhoneNumberBtn.isVisible = false
        binding.updatePasswordBtn.isVisible = true
        binding.logoutBtn.isVisible = true

        // Ensure user is logged in
        val uid = sharedPref.getId()
        Log.d(TAG_PROFILE, "Loaded UID from SharedPref: $uid")
        if (uid.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                "User ID not found. Please log in again.",
                Toast.LENGTH_LONG
            ).show()
            findNavController().navigate(R.id.action_profile_to_login)
            return
        }

        // Observe profile data
        viewModel.user.observe(viewLifecycleOwner) { user: UserModel ->
            Log.d(TAG_PROFILE, "Profile LiveData emitted: $user")


            // Header
            binding.profileName.text = "${user.name} ${user.lastName.orEmpty()}".trim()
            binding.profileEmail.text = user.email.orEmpty()

            // Populate fields
            binding.editUsername.setText(user.name)
            binding.editEmail.setText(user.email.orEmpty())

            originalPhoneNumber = user.phoneNumber.orEmpty()
            binding.editContact.setText(originalPhoneNumber)

            sharedPref.saveUserPassword(user.password.orEmpty())

            loadProfileImageFromLocalOrRemote(uid)

            // Set buttons based on original state
            updateButtons()
        }

        // Load profile from ViewModel
        viewModel.loadProfile(uid)

        // Listen for contact edits
        binding.editContact.addTextChangedListener {
            updateButtons()
        }

        // Update Phone Number click
        binding.updatePhoneNumberBtn.setOnClickListener {
            val newPhone = binding.editContact.text.toString()
            if (newPhone.isNotBlank()) {
                viewModel.updatePhoneNumber(newPhone)
            } else {
                Toast.makeText(
                    requireContext(),
                    "Enter a valid phone number",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Observe update result
        viewModel.updateStatus.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(
                    requireContext(),
                    "Phone number updated successfully!",
                    Toast.LENGTH_SHORT
                ).show()
                // Reset original and button state
                originalPhoneNumber = binding.editContact.text.toString()
                updateButtons()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Failed to update phone number. Try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Update Password click
        binding.updatePasswordBtn.setOnClickListener {
            ChangePasswordBottomSheet().show(parentFragmentManager, "ChangePassword")
        }

        // Notifications icon click
        binding.notificationIcon.setOnClickListener {
            showNotificationsDialog()
        }

        // Logout click
        binding.logoutBtn.setOnClickListener {
            sharedPref.clearUserData()
            findNavController().navigate(R.id.action_profile_to_login)
        }
        binding.profileImage.setOnClickListener {
            selectProfileImageFromGallery()
        }
    }

    /**
     * Toggle visibility:
     * - If contact text != originalPhoneNumber → show [Update Password, Update Phone], hide [Logout]
     * - Else                                    → show [Update Password, Logout], hide [Update Phone]
     */
    private fun updateButtons() {
        val edited = binding.editContact.text.toString() != originalPhoneNumber
        binding.updatePhoneNumberBtn.isVisible = edited
        binding.logoutBtn.isVisible            = !edited
        binding.updatePasswordBtn.isVisible    = true
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
    private fun selectProfileImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data?.data != null) {
            val imageUri = data.data!!
            uploadProfileImageToFirebase(imageUri)
        }
    }

    private fun uploadProfileImageToFirebase(imageUri: Uri) {
        val uid = sharedPref.getId() ?: return
        val ref = storage.reference.child("profile_pics/$uid.jpg")
        ref.putFile(imageUri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                    sharedPref.saveProfileImageUrl(downloadUrl.toString())
                    loadImageIntoProfileView(downloadUrl.toString())
                    Toast.makeText(requireContext(), "Profile picture updated!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Upload failed!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadProfileImageFromLocalOrRemote(uid: String) {
        val cachedUrl = sharedPref.getProfileImageUrl()
        if (!cachedUrl.isNullOrBlank()) {
            loadImageIntoProfileView(cachedUrl)
        } else {
            val ref = storage.reference.child("profile_pics/$uid.jpg")
            ref.downloadUrl.addOnSuccessListener { uri ->
                sharedPref.saveProfileImageUrl(uri.toString())
                loadImageIntoProfileView(uri.toString())
            }
        }
    }

    private fun loadImageIntoProfileView(url: String) {
        Glide.with(this)
            .load(url)
            .placeholder(R.drawable.ic_user)
            .circleCrop()
            .into(binding.profileImage)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
