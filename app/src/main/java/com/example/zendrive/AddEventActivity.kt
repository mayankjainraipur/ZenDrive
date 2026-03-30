package com.example.zendrive

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddEventActivity : AppCompatActivity() {

    private lateinit var viewModel: LogViewModel
    private var vehicleId: Int = -1
    private var eventDateMillis: Long = System.currentTimeMillis()
    private var nextDueDateMillis: Long? = null
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    
    // To keep track of dynamically added metadata fields
    private val metaFieldViews = mutableListOf<android.view.View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_event)

        vehicleId = intent.getIntExtra("vehicleId", -1)
        if (vehicleId == -1) {
            finish()
            return
        }

        val db = AppDatabase.getInstance(this)
        val factory = ViewModelFactory(db.vehicleDao(), db.vehicleEventDao(), db.eventMetaDao())
        viewModel = ViewModelProvider(this, factory)[LogViewModel::class.java]

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val actvEventType = findViewById<AutoCompleteTextView>(R.id.actvEventType)
        val etTitle = findViewById<TextInputEditText>(R.id.etTitle)
        val etDescription = findViewById<TextInputEditText>(R.id.etDescription)
        val etDate = findViewById<TextInputEditText>(R.id.etDate)
        val etCost = findViewById<TextInputEditText>(R.id.etCost)
        val etOdometer = findViewById<TextInputEditText>(R.id.etOdometer)
        val etNextDueDate = findViewById<TextInputEditText>(R.id.etNextDueDate)
        val metaContainer = findViewById<LinearLayout>(R.id.metaContainer)
        val btnAddMeta = findViewById<MaterialButton>(R.id.btnAddMeta)
        val btnSaveEvent = findViewById<MaterialButton>(R.id.btnSaveEvent)

        // Dropdown
        val eventTypes = resources.getStringArray(R.array.event_types)
        actvEventType.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, eventTypes))

        // Dates init
        etDate.setText(dateFormat.format(eventDateMillis))
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
            val view = LayoutInflater.from(this).inflate(R.layout.item_meta_field, metaContainer, false)
            val btnRemove = view.findViewById<ImageButton>(R.id.btnRemoveMeta)
            btnRemove.setOnClickListener {
                metaContainer.removeView(view)
                metaFieldViews.remove(view)
            }
            metaContainer.addView(view)
            metaFieldViews.add(view)
        }

        btnSaveEvent.setOnClickListener {
            val eventType = actvEventType.text.toString().trim()
            val title = etTitle.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val costStr = etCost.text.toString().trim()
            val odometerStr = etOdometer.text.toString().trim()

            if (eventType.isEmpty() || title.isEmpty()) {
                Toast.makeText(this, "Event Type and Title are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val cost = costStr.toDoubleOrNull()
            val odometer = odometerStr.toDoubleOrNull()

            val newEvent = VehicleEvent(
                vehicleId = vehicleId,
                eventType = eventType.lowercase(),
                title = title,
                description = description.ifEmpty { null },
                date = eventDateMillis,
                cost = cost,
                odometer = odometer,
                nextDueDate = nextDueDateMillis
            )

            // Extract metadata from dynamic views
            val extraMetaList = mutableListOf<Pair<String, String>>()
            for (view in metaFieldViews) {
                val etKey = view.findViewById<TextInputEditText>(R.id.etMetaKey)
                val etValue = view.findViewById<TextInputEditText>(R.id.etMetaValue)
                val key = etKey.text.toString().trim()
                val value = etValue.text.toString().trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    extraMetaList.add(Pair(key, value))
                }
            }

            // In LogViewModel we don't return ID from addEvent yet, so we have to use coroutines here directly 
            // or just let ViewModel handle it. I will do it directly via DAO to ensure atomic insert and ID capture for meta fields.
            androidx.lifecycle.lifecycleScope.launch {
                val eventId = db.vehicleEventDao().insertEvent(newEvent).toInt()
                if (extraMetaList.isNotEmpty()) {
                    val metaEntities = extraMetaList.map { 
                        EventMeta(eventId = eventId, key = it.first, value = it.second) 
                    }
                    db.eventMetaDao().insertAllMeta(metaEntities)
                }
                
                // If the user provided an odometer, update the vehicle's odometer too
                if (odometer != null) {
                    val vehicle = db.vehicleDao().getVehicleById(vehicleId)
                    if (vehicle != null && odometer > vehicle.odometerReading) {
                        db.vehicleDao().updateVehicle(vehicle.copy(odometerReading = odometer))
                    }
                }
                
                Toast.makeText(this@AddEventActivity, "Event saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
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
