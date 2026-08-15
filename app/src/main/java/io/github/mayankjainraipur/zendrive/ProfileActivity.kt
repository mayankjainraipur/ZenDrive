package io.github.mayankjainraipur.zendrive

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import android.widget.TextView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var viewModel: LogViewModel
    private lateinit var db: AppDatabase
    private var currentProfile: UserProfile? = null

    private lateinit var readSection: View
    private lateinit var editSection: View
    private lateinit var tvDisplayName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvMobile: TextView
    private lateinit var tvCurrency: TextView

    private lateinit var etDisplayName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etMobile: TextInputEditText
    private lateinit var etCurrency: TextInputEditText
    private lateinit var layoutDisplayName: TextInputLayout
    private lateinit var switchAppLock: MaterialSwitch

    private lateinit var actvTheme: MaterialAutoCompleteTextView
    private lateinit var actvDistanceUnit: MaterialAutoCompleteTextView
    private lateinit var actvDateFormat: MaterialAutoCompleteTextView
    private lateinit var actvReminderLead: MaterialAutoCompleteTextView

    /** Suppresses the change callbacks fired while binding values in from the profile. */
    private var bindingPreferences = false

    private val themeValues = listOf(UserPrefs.THEME_SYSTEM, UserPrefs.THEME_LIGHT, UserPrefs.THEME_DARK)
    private val distanceValues = listOf("km", "mi")
    private val leadDayValues = listOf(0, 1, 2, 3, 7, 14, 30)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        db = AppDatabase.getInstance(this)
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

        switchAppLock = findViewById(R.id.switchAppLock)

        findViewById<MaterialButton>(R.id.btnAddPersonalDoc).setOnClickListener {
            startActivity(
                android.content.Intent(this, AddDocumentActivity::class.java)
                    .putExtra("vehicleId", AddDocumentActivity.PERSONAL)
            )
        }

        actvTheme = findViewById(R.id.actvTheme)
        actvDistanceUnit = findViewById(R.id.actvDistanceUnit)
        actvDateFormat = findViewById(R.id.actvDateFormat)
        actvReminderLead = findViewById(R.id.actvReminderLead)
        setupPreferences()

        // Buttons
        findViewById<MaterialButton>(R.id.btnEdit).setOnClickListener { enterEditMode() }
        findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { enterReadMode() }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { saveProfile() }

        // Load profile
        lifecycleScope.launch {
            viewModel.userProfileFlow.collectLatest { profile ->
                currentProfile = profile
                profile?.let {
                    bindReadMode(it)
                    bindPreferences(it)
                    switchAppLock.setOnCheckedChangeListener(null)
                    switchAppLock.isChecked = it.appLockEnabled
                    switchAppLock.setOnCheckedChangeListener { _, isChecked ->
                        onAppLockToggled(isChecked)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadPersonalDocuments()
    }

    /** Documents with no vehicle: a licence belongs to the owner and outlives any one vehicle. */
    private fun loadPersonalDocuments() {
        val container = findViewById<View>(R.id.personalDocsContainer) as android.widget.LinearLayout
        val empty = findViewById<TextView>(R.id.tvNoPersonalDocs)
        lifecycleScope.launch {
            val documents = db.vehicleDocumentDao().getPersonalDocuments()
            container.removeAllViews()
            empty.visibility = if (documents.isEmpty()) View.VISIBLE else View.GONE

            for (document in documents) {
                val row = android.widget.LinearLayout(this@ProfileActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(0, 16, 0, 16)
                    isClickable = true
                    setOnClickListener {
                        runCatching {
                            startActivity(DocumentStore.viewIntent(this@ProfileActivity, document))
                        }
                    }
                }
                row.addView(
                    TextView(this@ProfileActivity).apply {
                        text = document.title
                        setTextColor(getColor(R.color.text_primary))
                        textSize = 15f
                    }
                )
                val expiry = document.expiresAt?.let { FormatUtil.formatDate(it) }
                row.addView(
                    TextView(this@ProfileActivity).apply {
                        text = listOfNotNull(document.documentType, expiry).joinToString(" · ")
                        setTextColor(getColor(R.color.text_hint))
                        textSize = 12f
                    }
                )
                container.addView(row)
            }
        }
    }

    private fun setupPreferences() {
        val themeLabels = listOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val distanceLabels = listOf(getString(R.string.unit_km), getString(R.string.unit_mi))
        val dateLabels = resources.getStringArray(R.array.date_format_labels).toList()
        val leadLabels = resources.getStringArray(R.array.reminder_lead_labels).toList()

        actvTheme.setSimpleItems(themeLabels.toTypedArray())
        actvDistanceUnit.setSimpleItems(distanceLabels.toTypedArray())
        actvDateFormat.setSimpleItems(dateLabels.toTypedArray())
        actvReminderLead.setSimpleItems(leadLabels.toTypedArray())

        actvTheme.setOnItemClickListener { _, _, position, _ ->
            savePreferences { it.copy(themeMode = themeValues[position]) }
            // savePreferences has already mirrored the new value, so this repaints correctly.
            UserPrefs.applyTheme()
        }
        actvDistanceUnit.setOnItemClickListener { _, _, position, _ ->
            savePreferences { it.copy(distanceUnit = distanceValues[position]) }
        }
        actvDateFormat.setOnItemClickListener { _, _, position, _ ->
            val patterns = resources.getStringArray(R.array.date_format_patterns)
            savePreferences { it.copy(dateFormatPattern = patterns[position]) }
        }
        actvReminderLead.setOnItemClickListener { _, _, position, _ ->
            savePreferences { it.copy(reminderLeadDays = leadDayValues[position]) }
        }
    }

    private fun bindPreferences(profile: UserProfile) {
        bindingPreferences = true

        val themeIndex = themeValues.indexOf(profile.themeMode).coerceAtLeast(0)
        actvTheme.setText(
            listOf(
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
            )[themeIndex],
            false
        )

        val unitIndex = distanceValues.indexOf(profile.distanceUnit).coerceAtLeast(0)
        actvDistanceUnit.setText(
            listOf(getString(R.string.unit_km), getString(R.string.unit_mi))[unitIndex], false
        )

        val patterns = resources.getStringArray(R.array.date_format_patterns)
        val dateIndex = patterns.indexOf(profile.dateFormatPattern).coerceAtLeast(0)
        actvDateFormat.setText(
            resources.getStringArray(R.array.date_format_labels)[dateIndex], false
        )

        val leadIndex = leadDayValues.indexOf(profile.reminderLeadDays).coerceAtLeast(0)
        actvReminderLead.setText(
            resources.getStringArray(R.array.reminder_lead_labels)[leadIndex], false
        )

        bindingPreferences = false
    }

    /** Preferences apply on selection — no edit/save round trip, same as the app-lock switch. */
    private fun savePreferences(change: (UserProfile) -> UserProfile) {
        if (bindingPreferences) return
        val profile = currentProfile ?: return
        val updated = change(profile).copy(updatedAt = System.currentTimeMillis())
        currentProfile = updated
        UserPrefs.mirror(updated)
        // Application-scoped: a theme change recreates this activity, and lifecycleScope would
        // cancel the write half-done — after which the profile flow would mirror the old value back.
        ZenDriveApp.instance.appScope.launch {
            db.userProfileDao().upsert(updated)
        }
        Toast.makeText(this, R.string.pref_saved, Toast.LENGTH_SHORT).show()
    }

    private fun onAppLockToggled(isChecked: Boolean) {
        val profile = currentProfile ?: return
        if (isChecked && !canUseBiometric()) {
            switchAppLock.isChecked = false
            Toast.makeText(this, R.string.biometric_not_available, Toast.LENGTH_LONG).show()
            return
        }
        val updated = profile.copy(
            appLockEnabled = isChecked,
            updatedAt = System.currentTimeMillis()
        )
        lifecycleScope.launch {
            db.userProfileDao().upsert(updated)
        }
        val msg = if (isChecked) R.string.app_lock_enabled else R.string.app_lock_disabled
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun canUseBiometric(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
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