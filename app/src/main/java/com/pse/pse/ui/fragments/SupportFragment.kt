package com.pse.pse.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.pse.pse.R
import com.pse.pse.databinding.FragmentSupportBinding

class SupportFragment : BaseFragment() {

    // ─── ViewBinding ──────────────────────────────────────────────────────────
    private var _binding: FragmentSupportBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSupportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        val menuIcon = view.findViewById<ImageView>(R.id.menuIcon)

        menuIcon.setOnClickListener {
            val drawerLayout = activity?.findViewById<DrawerLayout>(R.id.drawerLayout)
            drawerLayout?.openDrawer(GravityCompat.START)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
