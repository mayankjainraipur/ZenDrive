package com.example.zendrive

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddVehicleActivity : AppCompatActivity() {

    private lateinit var viewModel: LogViewModel
    private var purchaseDateMillis: Long? = null
    private var editVehicleId: Int = -1
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_vehicle)

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

        // Fields
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etNumber = findViewById<TextInputEditText>(R.id.etNumber)
        val actvType = findViewById<AutoCompleteTextView>(R.id.actvType)
        val actvFuelType = findViewById<AutoCompleteTextView>(R.id.actvFuelType)
        val etBrand = findViewById<TextInputEditText>(R.id.etBrand)
        val etModel = findViewById<TextInputEditText>(R.id.etModel)
        val etYear = findViewById<TextInputEditText>(R.id.etYear)
        val etPurchaseDate = findViewById<TextInputEditText>(R.id.etPurchaseDate)
        val etNotes = findViewById<TextInputEditText>(R.id.etNotes)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)

        // Dropdown adapters
        val vehicleTypes = resources.getStringArray(R.array.vehicle_types)
        actvType.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, vehicleTypes))

        val fuelTypes = resources.getStringArray(R.array.fuel_types)
        actvFuelType.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, fuelTypes))

        // Date picker
        etPurchaseDate.setOnClickListener { showDatePicker(etPurchaseDate) }

        // Check if editing
        editVehicleId = intent.getIntExtra("vehicleId", -1)
        if (editVehicleId != -1) {
            toolbar.title = getString(R.string.edit_vehicle)
            // Pre-fill fields
            val name = intent.getStringExtra("name") ?: ""
            val number = intent.getStringExtra("number") ?: ""
            val type = intent.getStringExtra("type") ?: ""
            val fuelType = intent.getStringExtra("fuelType") ?: ""
            val brand = intent.getStringExtra("brand") ?: ""
            val model = intent.getStringExtra("model") ?: ""
            val year = intent.getIntExtra("year", 0)
            val purchaseDate = intent.getLongExtra("purchaseDate", -1L)
            val notes = intent.getStringExtra("notes") ?: ""

            etName.setText(name)
            etNumber.setText(number)
            actvType.setText(type, false)
            actvFuelType.setText(fuelType, false)
            etBrand.setText(brand)
            etModel.setText(model)
            if (year > 0) etYear.setText(year.toString())
            if (purchaseDate > 0) {
                purchaseDateMillis = purchaseDate
                etPurchaseDate.setText(dateFormat.format(purchaseDate))
            }
            etNotes.setText(notes)
        }

        // Save
        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val number = etNumber.text.toString().trim()
            val type = actvType.text.toString().trim()
            val fuelType = actvFuelType.text.toString().trim()
            val brand = etBrand.text.toString().trim()
            val model = etModel.text.toString().trim()
            val yearStr = etYear.text.toString().trim()
            val notes = etNotes.text.toString().trim()

            if (name.isEmpty() || number.isEmpty() || type.isEmpty() || fuelType.isEmpty()
                || brand.isEmpty() || model.isEmpty() || yearStr.isEmpty()
            ) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val year = yearStr.toIntOrNull() ?: 0

            val vehicle = Vehicle(
                id = if (editVehicleId != -1) editVehicleId else 0,
                name = name,
                vehicleNumber = number,
                type = type.lowercase(),
                fuelType = fuelType.lowercase(),
                brand = brand,
                model = model,
                year = year,
                purchaseDate = purchaseDateMillis,
                notes = notes.ifEmpty { null }
            )

            if (editVehicleId != -1) {
                viewModel.updateVehicle(vehicle)
            } else {
                viewModel.addVehicle(vehicle)
            }

            Toast.makeText(this, "Vehicle saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showDatePicker(target: TextInputEditText) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                cal.set(year, month, day, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                purchaseDateMillis = cal.timeInMillis
                target.setText(dateFormat.format(cal.time))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}
