package io.github.mayankjainraipur.zendrive

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One search across vehicles, events, documents and reminders.
 *
 * Search previously stopped at the vehicle list, which is the smallest of the four and the one
 * least likely to be what you are looking for — the receipt from two years ago is an event.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var input: TextInputEditText
    private lateinit var results: LinearLayout
    private lateinit var hint: TextView

    /** Cancelled on each keystroke, so only the last query in a burst of typing runs. */
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        input = findViewById(R.id.etSearch)
        results = findViewById(R.id.resultsContainer)
        hint = findViewById(R.id.tvSearchHint)

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                val query = s?.toString()?.trim().orEmpty()
                if (query.length < MIN_QUERY) {
                    results.removeAllViews()
                    hint.setText(R.string.search_hint)
                    hint.visibility = View.VISIBLE
                    return
                }
                searchJob = lifecycleScope.launch {
                    delay(DEBOUNCE_MS)
                    runSearch(query)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })
    }

    private suspend fun runSearch(query: String) {
        val db = AppDatabase.getInstance(this)
        // LIKE needs its own wildcards, and the user's % or _ must not act as one.
        val pattern = "%" + query.replace("%", "\\%").replace("_", "\\_") + "%"

        val vehicles = db.vehicleDao().searchVehicles(pattern, PER_SECTION)
        val events = db.vehicleEventDao().searchEvents(pattern, PER_SECTION)
        val documents = db.vehicleDocumentDao().searchDocuments(pattern, PER_SECTION)
        val reminders = db.reminderDao().searchReminders(pattern, PER_SECTION)

        val vehicleNames = db.vehicleDao().getAllVehicles().associate { it.id to it.name }

        results.removeAllViews()
        val total = vehicles.size + events.size + documents.size + reminders.size
        if (total == 0) {
            hint.text = getString(R.string.search_no_results, query)
            hint.visibility = View.VISIBLE
            return
        }
        hint.visibility = View.GONE

        if (vehicles.isNotEmpty()) {
            addHeader(getString(R.string.search_section_vehicles))
            vehicles.forEach { vehicle ->
                addRow(vehicle.name, vehicle.vehicleNumber) {
                    startActivity(
                        Intent(this, VehicleDetailActivity::class.java)
                            .putExtra("vehicleId", vehicle.id)
                    )
                }
            }
        }

        if (events.isNotEmpty()) {
            addHeader(getString(R.string.search_section_events))
            events.forEach { event ->
                val subtitle = listOfNotNull(
                    vehicleNames[event.vehicleId],
                    FormatUtil.formatDate(event.date),
                    event.cost?.let { FormatUtil.formatCurrency(it) }
                ).joinToString(" · ")
                addRow(event.title, subtitle) {
                    startActivity(
                        Intent(this, EventDetailActivity::class.java)
                            .putExtra("eventId", event.id)
                    )
                }
            }
        }

        if (documents.isNotEmpty()) {
            addHeader(getString(R.string.search_section_documents))
            documents.forEach { document ->
                val subtitle = listOfNotNull(
                    vehicleNames[document.vehicleId],
                    document.documentType,
                    document.expiresAt?.let { FormatUtil.formatDate(it) }
                ).joinToString(" · ")
                addRow(document.title, subtitle) {
                    runCatching { startActivity(DocumentStore.viewIntent(this, document)) }
                }
            }
        }

        if (reminders.isNotEmpty()) {
            addHeader(getString(R.string.search_section_reminders))
            reminders.forEach { reminder ->
                val subtitle = listOfNotNull(
                    vehicleNames[reminder.vehicleId],
                    FormatUtil.formatDate(reminder.dueAt)
                ).joinToString(" · ")
                addRow(reminder.title, subtitle, null)
            }
        }
    }

    private fun addHeader(title: String) {
        results.addView(
            TextView(this).apply {
                text = title
                setTextColor(getColor(R.color.accent))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(4))
            }
        )
    }

    private fun addRow(title: String, subtitle: String?, onClick: (() -> Unit)?) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
            }
        }
        row.addView(
            TextView(this).apply {
                text = title
                setTextColor(getColor(R.color.text_primary))
                textSize = 15f
            }
        )
        if (!subtitle.isNullOrBlank()) {
            row.addView(
                TextView(this).apply {
                    text = subtitle
                    setTextColor(getColor(R.color.text_hint))
                    textSize = 12f
                }
            )
        }
        results.addView(row)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MIN_QUERY = 2
        private const val DEBOUNCE_MS = 220L
        private const val PER_SECTION = 8
    }
}
