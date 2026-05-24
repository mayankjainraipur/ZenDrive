package com.example.zendrive

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddReminderActivity : AppCompatActivity() {

    private var editingReminderId: Int = -1
    private var preselectedVehicleId: Int = -1
    private var dueDateMillis: Long = System.currentTimeMillis()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private var vehicles: List<Vehicle> = emptyList()
    private var selectedVehicleId: Int = -1
    private var events: List<VehicleEvent> = emptyList()
    private var selectedEventId: Int? = null

    private lateinit var toolbar: MaterialToolbar
    private lateinit var actvVehicle: AutoCompleteTextView
    private lateinit var actvReminderType: AutoCompleteTextView
    private lateinit var etTitle: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var etDueDate: TextInputEditText
    private lateinit var actvRepeatRule: AutoCompleteTextView
    private lateinit var actvLinkEvent: AutoCompleteTextView
    private lateinit var btnSave: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_reminder)

        val db = AppDatabase.getInstance(this)

        toolbar = findViewById(R.id.toolbar)
        actvVehicle = findViewById(R.id.actvVehicle)
        actvReminderType = findViewById(R.id.actvReminderType)
        etTitle = findViewById(R.id.etTitle)
        etDescription = findViewById(R.id.etDescription)
        etDueDate = findViewById(R.id.etDueDate)
        actvRepeatRule = findViewById(R.id.actvRepeatRule)
        actvLinkEvent = findViewById(R.id.actvLinkEvent)
        btnSave = findViewById(R.id.btnSaveReminder)

        toolbar.setNavigationOnClickListener { finish() }

        editingReminderId = intent.getIntExtra("reminderId", -1)
        preselectedVehicleId = intent.getIntExtra("vehicleId", -1)

        val reminderTypes = resources.getStringArray(R.array.reminder_types)
        actvReminderType.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, reminderTypes)
        )

        val repeatRules = resources.getStringArray(R.array.repeat_rules)
        actvRepeatRule.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, repeatRules)
        )
        actvRepeatRule.setText(repeatRules[0], false)

        etDueDate.setOnClickListener { showDatePicker() }
        etDueDate.setText(dateFormat.format(dueDateMillis))

        btnSave.setOnClickListener { saveReminder(db) }

        lifecycleScope.launch {
            vehicles = db.vehicleDao().getAllVehicles()
            val vehicleNames = vehicles.map { it.name }
            actvVehicle.setAdapter(
                ArrayAdapter(this@AddReminderActivity, android.R.layout.simple_dropdown_item_1line, vehicleNames)
            )

            actvVehicle.setOnItemClickListener { _, _, position, _ ->
                selectedVehicleId = vehicles[position].id
                loadEventsForVehicle(db, selectedVehicleId)
            }

            if (editingReminderId != -1) {
                loadExistingReminder(db)
            } else {
                toolbar.title = getString(R.string.add_reminder)
                if (preselectedVehicleId > 0) {
                    selectedVehicleId = preselectedVehicleId
                    val vehicle = vehicles.find { it.id == preselectedVehicleId }
                    vehicle?.let {
                        actvVehicle.setText(it.name, false)
                        loadEventsForVehicle(db, selectedVehicleId)
                    }
                }
            }
        }
    }

    private fun loadExistingReminder(db: AppDatabase) {
        lifecycleScope.launch {
            val reminder = db.reminderDao().getById(editingReminderId)
            if (reminder == null) {
                finish()
                return@launch
            }

            toolbar.title = getString(R.string.edit_reminder)
            selectedVehicleId = reminder.vehicleId
            selectedEventId = reminder.eventId
            dueDateMillis = reminder.dueAt

            val vehicle = vehicles.find { it.id == reminder.vehicleId }
            vehicle?.let { actvVehicle.setText(it.name, false) }

            val reminderTypes = resources.getStringArray(R.array.reminder_types)
            val reminderTypeValues = resources.getStringArray(R.array.reminder_type_values)
            val typeIndex = reminderTypeValues.indexOf(reminder.reminderType)
            if (typeIndex >= 0) {
                actvReminderType.setText(reminderTypes[typeIndex], false)
            }

            etTitle.setText(reminder.title)
            etDescription.setText(reminder.description.orEmpty())
            etDueDate.setText(dateFormat.format(reminder.dueAt))

            val repeatRules = resources.getStringArray(R.array.repeat_rules)
            val repeatRuleValues = resources.getStringArray(R.array.repeat_rule_values)
            val ruleIndex = repeatRuleValues.indexOf(reminder.repeatRule)
            if (ruleIndex >= 0) {
                actvRepeatRule.setText(repeatRules[ruleIndex], false)
            }

            loadEventsForVehicle(db, selectedVehicleId)
        }
    }

    private fun loadEventsForVehicle(db: AppDatabase, vehicleId: Int) {
        lifecycleScope.launch {
            events = db.vehicleEventDao().getEventsForVehicle(vehicleId)
            val eventTitles = listOf(getString(R.string.no_events_to_link)) +
                events.map { it.title }
            actvLinkEvent.setAdapter(
                ArrayAdapter(this@AddReminderActivity, android.R.layout.simple_dropdown_item_1line, eventTitles)
            )

            actvLinkEvent.setOnItemClickListener { _, _, position, _ ->
                selectedEventId = if (position == 0) null else events[position - 1].id
            }

            if (selectedEventId != null) {
                val linkedEvent = events.find { it.id == selectedEventId }
                linkedEvent?.let { actvLinkEvent.setText(it.title, false) }
            }
        }
    }

    private fun saveReminder(db: AppDatabase) {
        val title = etTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val typeDisplay = actvReminderType.text.toString().trim()
        val repeatDisplay = actvRepeatRule.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(this, R.string.field_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (typeDisplay.isEmpty()) {
            Toast.makeText(this, R.string.field_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedVehicleId <= 0) {
            Toast.makeText(this, R.string.field_required, Toast.LENGTH_SHORT).show()
            return
        }

        val reminderTypeValues = resources.getStringArray(R.array.reminder_type_values)
        val reminderTypes = resources.getStringArray(R.array.reminder_types)
        val typeIndex = reminderTypes.indexOf(typeDisplay)
        val reminderType = if (typeIndex >= 0) reminderTypeValues[typeIndex] else "custom"

        val repeatRuleValues = resources.getStringArray(R.array.repeat_rule_values)
        val repeatRules = resources.getStringArray(R.array.repeat_rules)
        val ruleIndex = repeatRules.indexOf(repeatDisplay)
        val repeatRule = if (ruleIndex >= 0) repeatRuleValues[ruleIndex] else "none"

        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            if (editingReminderId != -1) {
                val existing = db.reminderDao().getById(editingReminderId) ?: return@launch
                val updated = existing.copy(
                    vehicleId = selectedVehicleId,
                    eventId = selectedEventId,
                    title = title,
                    description = description.ifEmpty { null },
                    reminderType = reminderType,
                    dueAt = dueDateMillis,
                    repeatRule = repeatRule,
                    updatedAt = now
                )
                db.reminderDao().update(updated)
                Toast.makeText(this@AddReminderActivity, R.string.reminder_updated, Toast.LENGTH_SHORT).show()
            } else {
                val newReminder = Reminder(
                    vehicleId = selectedVehicleId,
                    eventId = selectedEventId,
                    title = title,
                    description = description.ifEmpty { null },
                    reminderType = reminderType,
                    dueAt = dueDateMillis,
                    repeatRule = repeatRule,
                    createdAt = now,
                    updatedAt = now
                )
                db.reminderDao().insert(newReminder)
                Toast.makeText(this@AddReminderActivity, R.string.reminder_saved, Toast.LENGTH_SHORT).show()
            }
            ReminderScheduler.scheduleOneTime(this@AddReminderActivity)
            finish()
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                cal.set(year, month, day, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                dueDateMillis = cal.timeInMillis
                etDueDate.setText(dateFormat.format(dueDateMillis))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}
