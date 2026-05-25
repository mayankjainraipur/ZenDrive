package com.example.zendrive

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
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

        editVehicleId = intent.getIntExtra("vehicleId", -1)
        if (editVehicleId != -1) {
            toolbar.title = getString(R.string.edit_vehicle)
            btnSave.isEnabled = false
            lifecycleScope.launch {
                val vehicle = AppDatabase.getInstance(this@AddVehicleActivity)
                    .vehicleDao().getVehicleById(editVehicleId)
                if (vehicle == null) {
                    finish()
                    return@launch
                }
                etName.setText(vehicle.name)
                etNumber.setText(vehicle.vehicleNumber)
                actvType.setText(vehicle.type.replaceFirstChar { it.uppercase() }, false)
                actvFuelType.setText(vehicle.fuelType.replaceFirstChar { it.uppercase() }, false)
                etBrand.setText(vehicle.brand)
                etModel.setText(vehicle.model)
                if (vehicle.year > 0) etYear.setText(vehicle.year.toString())
                if (vehicle.purchaseDate != null && vehicle.purchaseDate > 0) {
                    purchaseDateMillis = vehicle.purchaseDate
                    etPurchaseDate.setText(dateFormat.format(vehicle.purchaseDate))
                }
                etNotes.setText(vehicle.notes.orEmpty())
                btnSave.isEnabled = true
            }
        }

        val layoutName = findViewById<TextInputLayout>(R.id.layoutName)
        val layoutNumber = findViewById<TextInputLayout>(R.id.layoutNumber)
        val layoutYear = findViewById<TextInputLayout>(R.id.layoutYear)

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val number = etNumber.text.toString().trim()
            val type = actvType.text.toString().trim()
            val fuelType = actvFuelType.text.toString().trim()
            val brand = etBrand.text.toString().trim()
            val model = etModel.text.toString().trim()
            val yearStr = etYear.text.toString().trim()
            val notes = etNotes.text.toString().trim()

            var hasError = false
            layoutName.error = null
            layoutNumber.error = null
            layoutYear.error = null

            if (name.isEmpty()) { layoutName.error = getString(R.string.field_required); hasError = true }
            if (number.isEmpty()) { layoutNumber.error = getString(R.string.field_required); hasError = true }

            val year = yearStr.toIntOrNull()
            if (yearStr.isEmpty()) {
                layoutYear.error = getString(R.string.field_required); hasError = true
            } else if (year == null || year < 1900 || year > Calendar.getInstance().get(Calendar.YEAR) + 1) {
                layoutYear.error = getString(R.string.invalid_year); hasError = true
            }

            if (type.isEmpty() || fuelType.isEmpty() || brand.isEmpty() || model.isEmpty()) {
                Toast.makeText(this, getString(R.string.field_required), Toast.LENGTH_SHORT).show()
                hasError = true
            }

            if (hasError) return@setOnClickListener

            val vehicle = Vehicle(
                id = if (editVehicleId != -1) editVehicleId else 0,
                name = name,
                vehicleNumber = number,
                type = type.lowercase(),
                fuelType = fuelType.lowercase(),
                brand = brand,
                model = model,
                year = year!!,
                purchaseDate = purchaseDateMillis,
                notes = notes.ifEmpty { null }
            )

            if (editVehicleId != -1) {
                viewModel.updateVehicle(vehicle)
            } else {
                viewModel.addVehicle(vehicle)
            }

            finish()
        }
    }

    private fun showDatePicker(target: TextInputEditText) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.purchase_date))
            .setSelection(purchaseDateMillis ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            purchaseDateMillis = selection
            target.setText(dateFormat.format(selection))
        }
        picker.show(supportFragmentManager, "purchase_date_picker")
    }
}
