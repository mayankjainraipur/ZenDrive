package com.example.zendrive

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: LogViewModel
    private lateinit var adapter: VehicleAdapter

    private lateinit var onboardingFormRoot: View

    private var allVehicles: List<Vehicle> = emptyList()
    private var searchQuery: String = ""
    private var searchExpanded: Boolean = false

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

        val onboardingOverlay = findViewById<ScrollView>(R.id.onboardingOverlay)
        val onboardingSlot = findViewById<FrameLayout>(R.id.onboardingFormSlot)
        onboardingFormRoot = layoutInflater.inflate(R.layout.user_profile_form_fields, onboardingSlot, true)

        val recycler = findViewById<RecyclerView>(R.id.recyclerVehicles)
        val emptyState = findViewById<LinearLayout>(R.id.emptyState)
        val tvNoSearchMatches = findViewById<TextView>(R.id.tvNoSearchMatches)
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fabAddVehicle)
        val btnSearch = findViewById<ImageButton>(R.id.btnSearch)
        val btnProfile = findViewById<ImageButton>(R.id.btnProfile)
        val searchLayout = findViewById<TextInputLayout>(R.id.searchInputLayout)
        val editSearch = findViewById<TextInputEditText>(R.id.editSearch)

        adapter = VehicleAdapter { vehicle ->
            val intent = Intent(this, VehicleDetailActivity::class.java)
            intent.putExtra("vehicleId", vehicle.id)
            startActivity(intent)
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<MaterialButton>(R.id.btnContinueProfile).setOnClickListener {
            submitOnboardingProfile()
        }

        btnProfile.setOnClickListener {
            val p = currentProfile ?: return@setOnClickListener
            showProfileBottomSheet(p)
        }

        btnSearch.setOnClickListener {
            searchExpanded = !searchExpanded
            searchLayout.visibility = if (searchExpanded) View.VISIBLE else View.GONE
            if (searchExpanded) {
                editSearch.requestFocus()
                val imm =
                    getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(editSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            } else {
                editSearch.setText("")
                searchQuery = ""
                applyFilterAndUpdateUi(emptyState, tvNoSearchMatches, recycler)
                val imm =
                    getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(editSearch.windowToken, 0)
            }
        }

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                applyFilterAndUpdateUi(emptyState, tvNoSearchMatches, recycler)
            }
        })

        lifecycleScope.launch {
            viewModel.userProfileFlow.distinctUntilChanged().collectLatest { profile ->
                currentProfile = profile
                val showOnboarding = profile == null
                onboardingOverlay.visibility = if (showOnboarding) View.VISIBLE else View.GONE
                btnProfile.visibility = if (!showOnboarding) View.VISIBLE else View.GONE
                fab.visibility = if (!showOnboarding) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.vehicles.collectLatest { vehicles ->
                allVehicles = vehicles
                applyFilterAndUpdateUi(emptyState, tvNoSearchMatches, recycler)
            }
        }

        fab.setOnClickListener {
            startActivity(Intent(this, AddVehicleActivity::class.java))
        }
    }

    private fun submitOnboardingProfile() {
        val nameLayout = onboardingFormRoot.findViewById<TextInputLayout>(R.id.layoutDisplayName)
        val name =
            onboardingFormRoot.findViewById<TextInputEditText>(R.id.etDisplayName).text?.toString().orEmpty()
        if (name.isBlank()) {
            nameLayout.error = getString(R.string.profile_username_required)
            return
        }
        nameLayout.error = null
        viewModel.saveUserProfile(
            displayName = name,
            email = onboardingFormRoot.findViewById<TextInputEditText>(R.id.etEmail).text?.toString()
                .orEmpty(),
            mobile = onboardingFormRoot.findViewById<TextInputEditText>(R.id.etMobile).text?.toString(),
            currencyCode = onboardingFormRoot.findViewById<TextInputEditText>(R.id.etCurrency).text?.toString()
                .orEmpty(),
            existing = null
        )
    }

    private fun showProfileBottomSheet(profile: UserProfile) {
        val dialog = BottomSheetDialog(this)
        val content = layoutInflater.inflate(R.layout.bottom_sheet_user_profile, null, false)
        dialog.setContentView(content)

        val readSection = content.findViewById<LinearLayout>(R.id.profileReadSection)
        val editSlot = content.findViewById<FrameLayout>(R.id.profileEditFormSlot)
        val viewActions = content.findViewById<LinearLayout>(R.id.profileViewActions)
        val editActions = content.findViewById<LinearLayout>(R.id.profileEditActions)

        val tvReadDisplayName = content.findViewById<TextView>(R.id.tvReadDisplayName)
        val tvReadEmail = content.findViewById<TextView>(R.id.tvReadEmail)
        val tvReadMobile = content.findViewById<TextView>(R.id.tvReadMobile)
        val tvReadCurrency = content.findViewById<TextView>(R.id.tvReadCurrency)

        var editFormRoot: View? = null

        fun bindRead(p: UserProfile) {
            tvReadDisplayName.text = p.displayName
            tvReadEmail.text = p.email.takeIf { it.isNotBlank() }
                ?: getString(R.string.profile_empty_placeholder)
            tvReadMobile.text = p.mobileNumber?.takeIf { it.isNotBlank() }
                ?: getString(R.string.profile_empty_placeholder)
            tvReadCurrency.text = p.preferredCurrencyCode.takeIf { it.isNotBlank() }
                ?: getString(R.string.profile_empty_placeholder)
        }

        bindRead(profile)

        fun enterViewMode() {
            editFormRoot = null
            readSection.visibility = View.VISIBLE
            viewActions.visibility = View.VISIBLE
            editSlot.visibility = View.GONE
            editSlot.removeAllViews()
            editActions.visibility = View.GONE
            bindRead(profile)
        }

        content.findViewById<MaterialButton>(R.id.btnEditProfile).setOnClickListener {
            editSlot.removeAllViews()
            editFormRoot = layoutInflater.inflate(R.layout.user_profile_form_fields, editSlot, true)
            val form = editFormRoot!!
            form.findViewById<TextInputEditText>(R.id.etDisplayName).setText(profile.displayName)
            form.findViewById<TextInputEditText>(R.id.etEmail).setText(profile.email)
            form.findViewById<TextInputEditText>(R.id.etMobile).setText(profile.mobileNumber.orEmpty())
            form.findViewById<TextInputEditText>(R.id.etCurrency).setText(profile.preferredCurrencyCode)
            readSection.visibility = View.GONE
            viewActions.visibility = View.GONE
            editSlot.visibility = View.VISIBLE
            editActions.visibility = View.VISIBLE
        }

        content.findViewById<MaterialButton>(R.id.btnCancelEdit).setOnClickListener {
            enterViewMode()
        }

        content.findViewById<MaterialButton>(R.id.btnSaveProfile).setOnClickListener {
            val form = editFormRoot ?: return@setOnClickListener
            val nameLayout = form.findViewById<TextInputLayout>(R.id.layoutDisplayName)
            val name = form.findViewById<TextInputEditText>(R.id.etDisplayName).text?.toString().orEmpty()
            if (name.isBlank()) {
                nameLayout.error = getString(R.string.profile_username_required)
                return@setOnClickListener
            }
            nameLayout.error = null
            viewModel.saveUserProfile(
                displayName = name,
                email = form.findViewById<TextInputEditText>(R.id.etEmail).text?.toString().orEmpty(),
                mobile = form.findViewById<TextInputEditText>(R.id.etMobile).text?.toString(),
                currencyCode = form.findViewById<TextInputEditText>(R.id.etCurrency).text?.toString()
                    .orEmpty(),
                existing = profile
            )
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun filteredVehicles(): List<Vehicle> {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) return allVehicles
        return allVehicles.filter { v ->
            v.name.lowercase().contains(q) ||
                v.vehicleNumber.lowercase().contains(q) ||
                v.brand.lowercase().contains(q) ||
                v.model.lowercase().contains(q)
        }
    }

    private fun applyFilterAndUpdateUi(
        emptyState: LinearLayout,
        tvNoSearchMatches: TextView,
        recycler: RecyclerView
    ) {
        val filtered = filteredVehicles()
        adapter.submitList(filtered)

        when {
            allVehicles.isEmpty() -> {
                emptyState.visibility = View.VISIBLE
                tvNoSearchMatches.visibility = View.GONE
                recycler.visibility = View.GONE
            }
            filtered.isEmpty() && searchQuery.isNotBlank() -> {
                emptyState.visibility = View.GONE
                tvNoSearchMatches.visibility = View.VISIBLE
                recycler.visibility = View.GONE
            }
            else -> {
                emptyState.visibility = View.GONE
                tvNoSearchMatches.visibility = View.GONE
                recycler.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchVehicles()
    }
}
