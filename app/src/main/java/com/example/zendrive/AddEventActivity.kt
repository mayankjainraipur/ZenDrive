package com.example.zendrive

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.LinearLayout
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

class AddEventActivity : AppCompatActivity() {

    private var vehicleId: Int = -1
    private var editingEventId: Int = -1
    private var eventDateMillis: Long = System.currentTimeMillis()
    private var nextDueDateMillis: Long? = null
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private val metaFieldViews = mutableListOf<View>()

    private lateinit var toolbar: MaterialToolbar
    private lateinit var actvEventType: AutoCompleteTextView
    private lateinit var etTitle: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var etDate: TextInputEditText
    private lateinit var etCost: TextInputEditText
    private lateinit var etOdometer: TextInputEditText
    private lateinit var etNextDueDate: TextInputEditText
    private lateinit var metaContainer: LinearLayout
    private lateinit var btnAddMeta: MaterialButton
    private lateinit var btnSaveEvent: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_event)

        val db = AppDatabase.getInstance(this)

        toolbar = findViewById(R.id.toolbar)
        actvEventType = findViewById(R.id.actvEventType)
        etTitle = findViewById(R.id.etTitle)
        etDescription = findViewById(R.id.etDescription)
        etDate = findViewById(R.id.etDate)
        etCost = findViewById(R.id.etCost)
        etOdometer = findViewById(R.id.etOdometer)
        etNextDueDate = findViewById(R.id.etNextDueDate)
        metaContainer = findViewById(R.id.metaContainer)
        btnAddMeta = findViewById(R.id.btnAddMeta)
        btnSaveEvent = findViewById(R.id.btnSaveEvent)

        toolbar.setNavigationOnClickListener { finish() }

        editingEventId = intent.getIntExtra("eventId", -1)

        val eventTypes = resources.getStringArray(R.array.event_types)
        actvEventType.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, eventTypes)
        )

        etDate.setOnClickListener {
            showDatePicker { time ->
                eventDateMillis = time
                etDate.setText(dateFormat.format(time))
            }
        }

        etNextDueDate.setOnClickListener {
            showDatePicker { time ->
                nextDueDateMillis = time
                etNextDueDate.setText(dateFormat.format(time))
            }
        }

        btnAddMeta.setOnClickListener {
            addMetaFieldRow()
        }

        btnSaveEvent.setOnClickListener {
            saveEvent(db)
        }

        if (editingEventId != -1) {
            toolbar.title = getString(R.string.edit_event)
            btnSaveEvent.isEnabled = false
            lifecycleScope.launch {
                val event = db.vehicleEventDao().getEventById(editingEventId)
                if (event == null) {
                    finish()
                    return@launch
                }
                vehicleId = event.vehicleId
                eventDateMillis = event.date
                nextDueDateMillis = event.nextDueDate

                actvEventType.setText(
                    event.eventType.replaceFirstChar { c ->
                        if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
                    }
                )
                etTitle.setText(event.title)
                etDescription.setText(event.description.orEmpty())
                etDate.setText(dateFormat.format(event.date))
                etCost.setText(event.cost?.takeIf { it > 0 }?.let { formatDecimal(it) }.orEmpty())
                etOdometer.setText(event.odometer?.takeIf { it > 0 }?.let { formatDecimal(it) }.orEmpty())
                if (event.nextDueDate != null) {
                    etNextDueDate.setText(dateFormat.format(event.nextDueDate))
                } else {
                    etNextDueDate.text = null
                }

                metaContainer.removeAllViews()
                metaFieldViews.clear()
                db.eventMetaDao().getMetaForEvent(editingEventId).forEach { m ->
                    addMetaFieldRow(m.key, m.value)
                }

                btnSaveEvent.isEnabled = true
            }
        } else {
            vehicleId = intent.getIntExtra("vehicleId", -1)
            if (vehicleId == -1) {
                finish()
                return
            }
            toolbar.title = getString(R.string.add_event)
            etDate.setText(dateFormat.format(eventDateMillis))
        }
    }

    private fun formatDecimal(d: Double) =
        if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

    private fun addMetaFieldRow(initialKey: String = "", initialValue: String = "") {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_meta_field, metaContainer, false)

        view.findViewById<TextInputEditText>(R.id.etMetaKey).setText(initialKey)
        view.findViewById<TextInputEditText>(R.id.etMetaValue).setText(initialValue)

        view.findViewById<ImageButton>(R.id.btnRemoveMeta).setOnClickListener {
            metaContainer.removeView(view)
            metaFieldViews.remove(view)
        }

        metaContainer.addView(view)
        metaFieldViews.add(view)
    }

    private fun saveEvent(db: AppDatabase) {
        val eventType = actvEventType.text.toString().trim()
        val title = etTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val costStr = etCost.text.toString().trim()
        val odometerStr = etOdometer.text.toString().trim()

        if (eventType.isEmpty() || title.isEmpty()) {
            Toast.makeText(this, "Event Type and Title are required", Toast.LENGTH_SHORT).show()
            return
        }

        if (vehicleId == -1) {
            return
        }

        val cost = costStr.toDoubleOrNull()
        val odometer = odometerStr.toDoubleOrNull()

        val extraMetaList = metaFieldViews.mapNotNull { view ->
            val key = view.findViewById<TextInputEditText>(R.id.etMetaKey).text.toString().trim()
            val value = view.findViewById<TextInputEditText>(R.id.etMetaValue).text.toString().trim()
            if (key.isNotEmpty() && value.isNotEmpty()) Pair(key, value) else null
        }

        lifecycleScope.launch {
            if (editingEventId != -1) {
                val existing = db.vehicleEventDao().getEventById(editingEventId) ?: return@launch
                val updated = existing.copy(
                    eventType = eventType.lowercase(Locale.getDefault()),
                    title = title,
                    description = description.ifEmpty { null },
                    date = eventDateMillis,
                    cost = cost,
                    odometer = odometer,
                    nextDueDate = nextDueDateMillis
                )
                db.vehicleEventDao().updateEvent(updated)
                db.eventMetaDao().deleteAllMetaForEvent(editingEventId)
                if (extraMetaList.isNotEmpty()) {
                    db.eventMetaDao().insertAllMeta(
                        extraMetaList.map {
                            EventMeta(eventId = editingEventId, key = it.first, value = it.second)
                        }
                    )
                }
                syncVehicleOdometer(db, odometer)
                Toast.makeText(this@AddEventActivity, R.string.event_updated, Toast.LENGTH_SHORT).show()
            } else {
                val newEvent = VehicleEvent(
                    vehicleId = vehicleId,
                    eventType = eventType.lowercase(Locale.getDefault()),
                    title = title,
                    description = description.ifEmpty { null },
                    date = eventDateMillis,
                    cost = cost,
                    odometer = odometer,
                    nextDueDate = nextDueDateMillis
                )
                val newId = db.vehicleEventDao().insertEvent(newEvent).toInt()
                if (extraMetaList.isNotEmpty()) {
                    db.eventMetaDao().insertAllMeta(
                        extraMetaList.map {
                            EventMeta(eventId = newId, key = it.first, value = it.second)
                        }
                    )
                }
                syncVehicleOdometer(db, odometer)
                Toast.makeText(this@AddEventActivity, "Event saved!", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    private suspend fun syncVehicleOdometer(db: AppDatabase, odometer: Double?) {
        if (odometer == null) return
        val vehicle = db.vehicleDao().getVehicleById(vehicleId) ?: return
        if (odometer > vehicle.odometerReading) {
            db.vehicleDao().updateVehicle(vehicle.copy(odometerReading = odometer))
        }
    }

    private fun showDatePicker(onDateSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                cal.set(year, month, day, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                onDateSelected(cal.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}
