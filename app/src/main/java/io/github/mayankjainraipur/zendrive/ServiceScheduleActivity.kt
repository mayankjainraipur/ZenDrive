package io.github.mayankjainraipur.zendrive

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Recurring maintenance for one vehicle, each item due on whichever of distance or time arrives
 * first. Kept on its own screen because the vehicle detail is already long.
 */
class ServiceScheduleActivity : AppCompatActivity() {

    private var vehicleId: Int = -1
    private var vehicle: Vehicle? = null

    private lateinit var container: LinearLayout
    private lateinit var emptyState: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_schedule)

        vehicleId = intent.getIntExtra("vehicleId", -1)
        if (vehicleId == -1) {
            finish()
            return
        }

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        container = findViewById(R.id.scheduleContainer)
        emptyState = findViewById(R.id.emptyState)

        findViewById<MaterialButton>(R.id.btnAddItem).setOnClickListener { showItemDialog(null) }
        findViewById<MaterialButton>(R.id.btnUseTemplate).setOnClickListener { applyTemplate() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            vehicle = db.vehicleDao().getVehicleById(vehicleId)
            findViewById<MaterialToolbar>(R.id.toolbar).subtitle = vehicle?.name

            val schedules = db.serviceScheduleDao().getForVehicle(vehicleId)
            val usage = OdometerSync.statsFor(db, vehicleId)

            container.removeAllViews()
            emptyState.visibility = if (schedules.isEmpty()) View.VISIBLE else View.GONE

            for (schedule in schedules) {
                container.addView(buildRow(db, schedule, usage))
            }
        }
    }

    private fun buildRow(
        db: AppDatabase,
        schedule: ServiceSchedule,
        usage: OdometerSync.UsageStats
    ): View {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            radius = dp(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(getColor(R.color.card_bg))
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        body.addView(text(schedule.itemName, R.color.text_primary, 16f, bold = true))
        body.addView(text(intervalDescription(schedule), R.color.text_hint, 12f))
        body.addView(text(dueDescription(schedule, usage), dueColour(schedule, usage), 13f).apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(6)
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        actions.addView(textButton(getString(R.string.service_mark_done)) { markDone(db, schedule) })
        actions.addView(textButton(getString(R.string.edit_profile)) { showItemDialog(schedule) })
        actions.addView(textButton(getString(R.string.action_delete)) { confirmDelete(db, schedule) })
        body.addView(actions)

        card.addView(body)
        return card
    }

    private fun intervalDescription(schedule: ServiceSchedule): String {
        val unit = UserPrefs.distanceUnit
        val parts = buildList {
            schedule.intervalKm?.let {
                add(String.format(Locale.getDefault(), "%,.0f %s", it, unit))
            }
            schedule.intervalMonths?.let { add(resources.getQuantityString(R.plurals.months, it, it)) }
        }
        return if (parts.isEmpty()) getString(R.string.service_no_interval)
        else getString(R.string.service_every, parts.joinToString(" / "))
    }

    private fun dueDescription(
        schedule: ServiceSchedule,
        usage: OdometerSync.UsageStats
    ): String {
        if (schedule.lastDoneAt == null && schedule.lastDoneOdometer == null) {
            return getString(R.string.service_never_done)
        }

        val remaining = schedule.remainingDistance(usage.latestReading)
        val dueDate = schedule.projectedDueDate(usage)

        // Distance is the more actionable number when it is known, so it leads.
        return when {
            remaining != null && remaining <= 0 -> getString(R.string.service_overdue)
            dueDate != null && ServiceSchedule.daysUntil(dueDate) < 0 ->
                getString(R.string.service_overdue)
            remaining != null && dueDate != null -> getString(
                R.string.service_due_in_both,
                String.format(Locale.getDefault(), "%,.0f", remaining),
                UserPrefs.distanceUnit,
                FormatUtil.formatDate(dueDate)
            )
            remaining != null -> getString(
                R.string.service_due_in_distance,
                String.format(Locale.getDefault(), "%,.0f", remaining),
                UserPrefs.distanceUnit
            )
            dueDate != null -> getString(R.string.service_due_on, FormatUtil.formatDate(dueDate))
            else -> getString(R.string.service_no_estimate)
        }
    }

    private fun dueColour(schedule: ServiceSchedule, usage: OdometerSync.UsageStats): Int {
        val remaining = schedule.remainingDistance(usage.latestReading)
        val dueDate = schedule.projectedDueDate(usage)
        val overdue = (remaining != null && remaining <= 0) ||
            (dueDate != null && ServiceSchedule.daysUntil(dueDate) < 0)
        val soon = (remaining != null && remaining < 500) ||
            (dueDate != null && ServiceSchedule.daysUntil(dueDate) in 0..14)
        return when {
            overdue -> R.color.error
            soon -> R.color.warning
            else -> R.color.text_secondary
        }
    }

    /** Records the service as done now, at the current odometer, restarting both intervals. */
    private fun markDone(db: AppDatabase, schedule: ServiceSchedule) {
        lifecycleScope.launch {
            val current = db.vehicleDao().getVehicleById(vehicleId)?.odometerReading
            db.serviceScheduleDao().update(
                schedule.copy(
                    lastDoneAt = System.currentTimeMillis(),
                    lastDoneOdometer = current,
                    updatedAt = System.currentTimeMillis()
                )
            )
            ReminderSync.reconcile(db)
            Toast.makeText(this@ServiceScheduleActivity, R.string.service_marked_done, Toast.LENGTH_SHORT).show()
            load()
        }
    }

    private fun applyTemplate() {
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            val type = vehicle?.type ?: "car"
            val templates = ServiceSchedule.templatesFor(type)
                .map { it.copy(vehicleId = vehicleId) }
            db.serviceScheduleDao().insertAll(templates)
            ReminderSync.reconcile(db)
            Toast.makeText(
                this@ServiceScheduleActivity,
                getString(R.string.service_template_added, templates.size),
                Toast.LENGTH_SHORT
            ).show()
            load()
        }
    }

    private fun showItemDialog(existing: ServiceSchedule?) {
        val db = AppDatabase.getInstance(this)

        val nameField = field(getString(R.string.service_item_name), InputType.TYPE_CLASS_TEXT)
            .apply { setText(existing?.itemName.orEmpty()) }
        val kmField = field(
            getString(R.string.service_interval_distance, UserPrefs.distanceUnit),
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        ).apply { setText(existing?.intervalKm?.let { String.format(Locale.getDefault(), "%.0f", it) }.orEmpty()) }
        val monthsField = field(getString(R.string.service_interval_months), InputType.TYPE_CLASS_NUMBER)
            .apply { setText(existing?.intervalMonths?.toString().orEmpty()) }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(nameField)
            addView(kmField)
            addView(monthsField)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.service_schedule_add_item else R.string.service_edit_item)
            .setView(layout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameField.text?.toString()?.trim().orEmpty()
                val km = kmField.text?.toString()?.toDoubleOrNull()
                val months = monthsField.text?.toString()?.toIntOrNull()

                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.field_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                // With neither interval there is nothing to become due, so the item would sit
                // inert forever.
                if (km == null && months == null) {
                    Toast.makeText(this, R.string.service_needs_an_interval, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val now = System.currentTimeMillis()
                    if (existing == null) {
                        db.serviceScheduleDao().insert(
                            ServiceSchedule(
                                vehicleId = vehicleId,
                                itemName = name,
                                intervalKm = km,
                                intervalMonths = months
                            )
                        )
                    } else {
                        db.serviceScheduleDao().update(
                            existing.copy(
                                itemName = name,
                                intervalKm = km,
                                intervalMonths = months,
                                updatedAt = now
                            )
                        )
                    }
                    ReminderSync.reconcile(db)
                    load()
                }
            }
            .show()
    }

    private fun confirmDelete(db: AppDatabase, schedule: ServiceSchedule) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_delete)
            .setMessage(getString(R.string.service_delete_confirm, schedule.itemName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    db.serviceScheduleDao().delete(schedule)
                    ReminderSync.reconcile(db)
                    load()
                }
            }
            .show()
    }

    private fun field(hint: String, type: Int) = TextInputEditText(this).apply {
        this.hint = hint
        inputType = type
        setTextColor(getColor(R.color.text_primary))
    }

    private fun text(value: String, colourRes: Int, sizeSp: Float, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            setTextColor(getColor(colourRes))
            textSize = sizeSp
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

    private fun textButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(getColor(R.color.accent))
        textSize = 13f
        setPadding(dp(12), dp(8), dp(12), dp(8))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
