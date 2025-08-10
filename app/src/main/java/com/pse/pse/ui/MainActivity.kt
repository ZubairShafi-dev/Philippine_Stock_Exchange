package com.pse.pse.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.pse.pse.R
import com.pse.pse.databinding.ActivityMainBinding
import com.pse.pse.ui.viewModels.ProfileViewModel
import com.pse.pse.utils.RemoteUpdateManager
import com.pse.pse.utils.SharedPrefManager

class MainActivity : AppCompatActivity() {
    private lateinit var updater: RemoteUpdateManager
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var cameFromDrawer = false
    private var currentUid: String? = null


    private val viewModel: ProfileViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(this.application)
    }

    companion object {
        private const val TAG = "ProfileFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updater = RemoteUpdateManager(this)
        updater.clearFlagsIfUpdated()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle incoming “?ref=…” param
        intent?.data?.getQueryParameter("ref")?.let { referrerId ->
            SharedPrefManager(this).saveReferralFromLink(referrerId)
        }

        val prefs = SharedPrefManager(this)
        currentUid = prefs.getId()
        if (!currentUid.isNullOrBlank()) {
            viewModel.loadProfile(currentUid!!)
        }

        // Observe profile
        viewModel.user.observe(this, Observer { user ->
            Log.d(
                TAG,
                "Profile LiveData emitted: $user"
            )
            binding.customDrawerHeader.userNameTextView.text =
                "${user.name} ${user.lastName.orEmpty()}".trim()
            binding.customDrawerHeader.userEmailTextView.text = user.email.orEmpty()

            SharedPrefManager(this).getProfileImageUrl()?.takeIf { it.isNotBlank() }
                ?.let { savedUrl ->
                    val drawerImageView = binding.customDrawerHeader.drawerProfileImageView
                    Glide.with(this)
                        .load(savedUrl)
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .circleCrop()
                        .into(drawerImageView)
                }
        })

        // Reload header when uid changes
        fun refreshHeader() {
            val newUid = SharedPrefManager(this).getId() ?: return
            if (newUid == currentUid) return
            currentUid = newUid
            viewModel.loadProfile(newUid)
        }
        refreshHeader()

        // NavController setup
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController

        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
        navGraph.setStartDestination(
            if (prefs.checkLogin()) R.id.homeFragment else R.id.splashFragment
        )
        navController.graph = navGraph

        // Bottom Nav logic
        binding.bottomNavBar.setOnItemSelectedListener { item ->
            val currentDestinationId = navController.currentDestination?.id
            if (item.itemId == currentDestinationId) {
                // Already on that fragment → do nothing
                return@setOnItemSelectedListener true
            }

            if (item.itemId == R.id.homeFragment) {
                navController.popBackStack(R.id.homeFragment, false)
            } else {
                val options = NavOptions.Builder()
                    .setEnterAnim(R.anim.slide_in_top)
                    .setExitAnim(R.anim.slide_out_bottom)
                    .setPopEnterAnim(R.anim.slide_in_bottom)
                    .setPopExitAnim(R.anim.slide_out_top)
                    .setPopUpTo(R.id.homeFragment, false)
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .build()
                navController.navigate(item.itemId, null, options)
            }
            true
        }

        // Drawer items
        val drawerItems = mapOf(
            R.id.menuHome to R.id.homeFragment,
            R.id.menuDeposit to R.id.depositAmountFragment,
            R.id.menuWithdraw to R.id.withdrawAmountFragment,
            R.id.menuTeam to R.id.teamRankingFragment,
            R.id.menuSupport to R.id.chatFragment,
            R.id.menuSalary to R.id.salaryIncomeFragment,
            R.id.menuLeaderShip to R.id.leadershipFragment,
            R.id.investmentPlans to R.id.planFragment,
            R.id.txnHistory to R.id.transactionHistoryFragment
        )

        drawerItems.forEach { (menuId, destId) ->
            binding.navigationView.findViewById<View>(menuId).setOnClickListener {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                navController.navigate(destId)
            }
        }

        // Logout menu listener
        binding.navigationView.findViewById<View>(R.id.logout).setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            showLogoutConfirmation()
        }

        // Hide bottom nav on select screens
        val hideNavScreens = setOf(
            R.id.signInFragment,
            R.id.signUpFragment,
            R.id.splashFragment,
            R.id.detailChatFragment,
            R.id.chatFragment
        )

        navController.addOnDestinationChangedListener { _, destination: NavDestination, _ ->
            binding.bottomNavBar.menu.findItem(destination.id)?.isChecked = true
            when {
                cameFromDrawer -> cameFromDrawer = false
                destination.id in hideNavScreens -> {
                    binding.bottomNavBar.alpha = 0f
                    binding.bottomNavBar.visibility = View.GONE
                }

                else -> {
                    binding.bottomNavBar.alpha = 1f
                    binding.bottomNavBar.visibility = View.VISIBLE
                }
            }
        }

        // Drawer animation listener
        binding.drawerLayout.addDrawerListener(object :
            androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                binding.bottomNavBar.animate().alpha(1f - slideOffset).setDuration(50).start()
                if (binding.bottomNavBar.visibility != View.VISIBLE && slideOffset < 1f) {
                    binding.bottomNavBar.visibility = View.VISIBLE
                }
            }

            override fun onDrawerOpened(drawerView: View) {
                refreshHeader()
                viewModel.user.value?.let { user ->
                    binding.customDrawerHeader.userNameTextView.text =
                        "${user.name} ${user.lastName.orEmpty()}".trim()
                    binding.customDrawerHeader.userEmailTextView.text = user.email.orEmpty()
                }
                binding.bottomNavBar.animate().alpha(0f).setDuration(150).start()
            }

            override fun onDrawerClosed(drawerView: View) {
                val currentDest = navController.currentDestination?.id
                if (currentDest !in hideNavScreens) {
                    binding.bottomNavBar.animate().alpha(1f).setDuration(150).start()
                }
            }

            override fun onDrawerStateChanged(newState: Int) {}
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 2) Your old drawer logic:
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    // 3) No drawer open → fallback to default behavior:
                    //    disable this callback so next back press falls through
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /** Show confirmation before logging out */
    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Logout")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Yes") { _, _ -> logout() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Clears user data and navigates to SignUp, clearing backstack */
    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        SharedPrefManager(this).clearUserData()
        navController.navigate(
            R.id.signInFragment,
            null,
            NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .build()
        )
    }

    fun openDrawer() = binding.drawerLayout.openDrawer(GravityCompat.START)

    override fun onResume() {
        super.onResume()
        Log.d("UpdateMgr", "MainActivity onResume - About to call checkForUpdate()")
        updater.checkForUpdate()
    }
}