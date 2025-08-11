package com.pse.pse.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.storage.FirebaseStorage
import com.pse.pse.R
import com.pse.pse.ui.MainActivity
import com.pse.pse.utils.SharedPrefManager

open class BaseFragment : Fragment() {

    private var loadingOverlay: View? = null

    /** Setup drawer trigger + bind avatar if the layout has profileIcon */
    fun setupDrawerTrigger(view: View) {
        view.findViewById<ImageView>(R.id.menuIcon)?.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }
        bindTopBarAvatarIfPresent(view)   // ← NEW
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ensure we can overlay on ScrollView roots
        var container: ViewGroup = view as? ViewGroup ?: return
        if (view is ScrollView && view.childCount == 1) {
            val originalChild = view.getChildAt(0)
            view.removeView(originalChild)
            val frameLayout = FrameLayout(requireContext()).apply {
                layoutParams = originalChild.layoutParams
                addView(originalChild)
            }
            view.addView(frameLayout)
            container = frameLayout
        }

        loadingOverlay =
            LayoutInflater.from(context).inflate(R.layout.loading_overlay, container, false)
        container.addView(loadingOverlay)
    }

    fun showLoading() {
        Log.d("BaseFragment", "showLoading called")
        loadingOverlay?.apply {
            visibility = View.VISIBLE
            bringToFront()
            elevation = 100f
            requestLayout()
        }
    }

    fun hideLoading() {
        Log.d("BaseFragment", "hideLoading called")
        loadingOverlay?.visibility = View.GONE
    }

    fun showError(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG).show() }
    }

    /* ───────────────────── Avatar helpers ───────────────────── */

    private fun bindTopBarAvatarIfPresent(root: View) {
        val iv = root.findViewById<ImageView>(R.id.profileIcon) ?: return
        val ctx = iv.context
        val prefs = SharedPrefManager(ctx)
        val uid = prefs.getId() ?: return

        val cached = prefs.getProfileImageUrl()
        if (!cached.isNullOrBlank()) {
            // View-scoped Glide → safe across fragment lifecycles
            Glide.with(iv)
                .load(cached)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .circleCrop()
                .into(iv)
            return
        }

        // No cached URL → try Firebase once, then cache
        FirebaseStorage.getInstance()
            .reference.child("profile_pics/$uid.jpg")
            .downloadUrl
            .addOnSuccessListener { uri ->
                prefs.saveProfileImageUrl(uri.toString())
                if (view != null) {
                    Glide.with(iv)
                        .load(uri)
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .circleCrop()
                        .into(iv)
                }
            }
            .addOnFailureListener {
                // ignore if the user has no photo yet
            }
    }
}