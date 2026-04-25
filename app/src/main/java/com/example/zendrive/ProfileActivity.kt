package com.example.zendrive

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var viewModel: LogViewModel
    private var currentProfile: UserProfile? = null

    private lateinit var readSection: View
    private lateinit var editSection: View
    private lateinit var tvDisplayName: TextInputEditText
    private lateinit var tvEmail: TextInputEditText
    private lateinit var tvMobile: TextInputEditText
    private lateinit var tvCurrency: TextInputEditText

    private lateinit var etDisplayName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etMobile: TextInputEditText
    private lateinit var etCurrency: TextInputEditText
    private lateinit var layoutDisplayName: TextInputLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val db = AppDatabase.getInstance(this)
        val factory = ViewModelFactory(
            db.vehicleDao(),
            db.vehicleEventDao(),
            db.eventMetaDao(),
            db.userProfileDao()
        )
        viewModel = ViewModelProvider(this, factory)[LogViewModel::class.java]

        // Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // Views
        readSection = findViewById(R.id.profileReadSection)
        editSection = findViewById(R.id.profileEditSection)

        tvDisplayName = findViewById(R.id.tvDisplayName)
        tvEmail = findViewById(R.id.tvEmail)
        tvMobile = findViewById(R.id.tvMobile)
        tvCurrency = findViewById(R.id.tvCurrency)

        etDisplayName = findViewById(R.id.etDisplayName)
        etEmail = findViewById(R.id.etEmail)
        etMobile = findViewById(R.id.etMobile)
        etCurrency = findViewById(R.id.etCurrency)
        layoutDisplayName = findViewById(R.id.layoutDisplayName)

        // Buttons
        findViewById<MaterialButton>(R.id.btnEdit).setOnClickListener { enterEditMode() }
        findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { enterReadMode() }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { saveProfile() }

        // Load profile
        lifecycleScope.launch {
            viewModel.userProfileFlow.collectLatest { profile ->
                currentProfile = profile
                profile?.let { bindReadMode(it) }
            }
        }
    }

    private fun bindReadMode(profile: UserProfile) {
        tvDisplayName.setText(profile.displayName)
        tvEmail.setText(profile.email.takeIf { it.isNotBlank() } ?: getString(R.string.profile_empty_placeholder))
        tvMobile.setText(profile.mobileNumber?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_empty_placeholder))
        tvCurrency.setText(profile.preferredCurrencyCode.takeIf { it.isNotBlank() } ?: getString(R.string.profile_empty_placeholder))
    }

    private fun enterEditMode() {
        val profile = currentProfile ?: return

        etDisplayName.setText(profile.displayName)
        etEmail.setText(profile.email)
        etMobile.setText(profile.mobileNumber.orEmpty())
        etCurrency.setText(profile.preferredCurrencyCode)
        layoutDisplayName.error = null

        readSection.visibility = View.GONE
        editSection.visibility = View.VISIBLE
    }

    private fun enterReadMode() {
        readSection.visibility = View.VISIBLE
        editSection.visibility = View.GONE
        layoutDisplayName.error = null
    }

    private fun saveProfile() {
        val name = etDisplayName.text?.toString().orEmpty().trim()
        if (name.isBlank()) {
            layoutDisplayName.error = getString(R.string.profile_username_required)
            return
        }
        layoutDisplayName.error = null

        viewModel.saveUserProfile(
            displayName = name,
            email = etEmail.text?.toString().orEmpty().trim(),
            mobile = etMobile.text?.toString()?.trim(),
            currencyCode = etCurrency.text?.toString().orEmpty().trim(),
            existing = currentProfile
        )

        Toast.makeText(this, getString(R.string.save), Toast.LENGTH_SHORT).show()
        enterReadMode()
    }
}
