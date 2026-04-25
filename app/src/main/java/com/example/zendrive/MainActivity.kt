package com.example.zendrive

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: LogViewModel
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var onboardingOverlay: ScrollView
    private lateinit var fragmentContainer: FrameLayout
    private lateinit var onboardingFormRoot: View

    /** Latest profile from DB; avoids stale null from StateFlow before Room emits. */
    private var currentProfile: UserProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val db = AppDatabase.getInstance(this)
        val factory = ViewModelFactory(
            db.vehicleDao(),
            db.vehicleEventDao(),
            db.eventMetaDao(),
            db.userProfileDao()
        )
        viewModel = ViewModelProvider(this, factory)[LogViewModel::class.java]

        onboardingOverlay = findViewById(R.id.onboardingOverlay)
        fragmentContainer = findViewById(R.id.fragmentContainer)
        bottomNav = findViewById(R.id.bottomNav)

        val onboardingSlot = findViewById<FrameLayout>(R.id.onboardingFormSlot)
        onboardingFormRoot = layoutInflater.inflate(R.layout.user_profile_form_fields, onboardingSlot, true)

        findViewById<MaterialButton>(R.id.btnContinueProfile).setOnClickListener {
            submitOnboardingProfile()
        }

        // Set up bottom navigation
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_vehicles -> {
                    showFragment(VehiclesFragment::class.java.simpleName)
                    true
                }
                R.id.nav_expenses -> {
                    showFragment(ExpensesFragment::class.java.simpleName)
                    true
                }
                R.id.nav_sync -> {
                    showFragment(SyncFragment::class.java.simpleName)
                    true
                }
                else -> false
            }
        }

        // Observe profile to show/hide onboarding
        lifecycleScope.launch {
            viewModel.userProfileFlow.collectLatest { profile ->
                currentProfile = profile
                val showOnboarding = profile == null
                onboardingOverlay.visibility = if (showOnboarding) View.VISIBLE else View.GONE
                bottomNav.visibility = if (!showOnboarding) View.VISIBLE else View.GONE

                if (!showOnboarding && supportFragmentManager.fragments.isEmpty()) {
                    // Load default fragment
                    showFragment(VehiclesFragment::class.java.simpleName)
                    bottomNav.selectedItemId = R.id.nav_vehicles
                }
            }
        }

        // Load default fragment if not showing onboarding
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                viewModel.userProfileFlow.collectLatest { profile ->
                    if (profile != null && supportFragmentManager.fragments.isEmpty()) {
                        showFragment(VehiclesFragment::class.java.simpleName)
                        bottomNav.selectedItemId = R.id.nav_vehicles
                    }
                }
            }
        }
    }

    private fun showFragment(tag: String) {
        val fragmentManager = supportFragmentManager
        val currentFragment = fragmentManager.fragments.find { it.isVisible }

        // Hide current fragment
        currentFragment?.let {
            fragmentManager.beginTransaction().hide(it).commitNow()
        }

        // Show or create new fragment
        var fragment = fragmentManager.findFragmentByTag(tag)
        if (fragment == null) {
            fragment = when (tag) {
                VehiclesFragment::class.java.simpleName -> VehiclesFragment()
                ExpensesFragment::class.java.simpleName -> ExpensesFragment()
                SyncFragment::class.java.simpleName -> SyncFragment()
                else -> VehiclesFragment()
            }
            fragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, fragment, tag)
                .commitNow()
        } else {
            fragmentManager.beginTransaction().show(fragment).commitNow()
        }
    }

    private fun submitOnboardingProfile() {
        val nameLayout = onboardingFormRoot.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutDisplayName)
        val name =
            onboardingFormRoot.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDisplayName).text?.toString().orEmpty()
        if (name.isBlank()) {
            nameLayout.error = getString(R.string.profile_username_required)
            return
        }
        nameLayout.error = null
        viewModel.saveUserProfile(
            displayName = name,
            email = onboardingFormRoot.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmail).text?.toString()
                .orEmpty(),
            mobile = onboardingFormRoot.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMobile).text?.toString(),
            currencyCode = onboardingFormRoot.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCurrency).text?.toString()
                .orEmpty(),
            existing = null
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchVehicles()
    }
}
