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
import com.google.android.material.snackbar.Snackbar
import com.pse.pse.R
import com.pse.pse.ui.MainActivity

open class BaseFragment : Fragment() {

    private var loadingOverlay: View? = null

    /** Setup drawer trigger via menu icon in fragment layout */
    fun setupDrawerTrigger(view: View) {
        view.findViewById<ImageView>(R.id.menuIcon)?.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Determine the container to add the overlay.
        var container: ViewGroup = view as? ViewGroup ?: return

        // If the root view is a ScrollView with one child, wrap that child in a FrameLayout.
        if (view is ScrollView && view.childCount == 1) {
            val originalChild = view.getChildAt(0)
            view.removeView(originalChild)
            val frameLayout = FrameLayout(requireContext())
            frameLayout.layoutParams = originalChild.layoutParams
            frameLayout.addView(originalChild)
            view.addView(frameLayout)
            container = frameLayout
        }

        // Inflate and add the loading overlay to the container.
        loadingOverlay =
            LayoutInflater.from(context).inflate(R.layout.loading_overlay, container, false)
        container.addView(loadingOverlay)
    }

    fun showLoading() {
        Log.d("BaseFragment", "showLoading called")
        loadingOverlay?.apply {
            visibility = View.VISIBLE
            // Bring the overlay to the front and set a high elevation to ensure it appears above everything.
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
        view?.let { root ->
            Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
        }
    }
}
