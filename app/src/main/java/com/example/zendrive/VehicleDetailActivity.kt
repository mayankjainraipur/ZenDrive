package com.example.zendrive

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VehicleDetailActivity : AppCompatActivity() {

    private lateinit var viewModel: LogViewModel
    private var vehicleId: Int = -1
    private lateinit var eventAdapter: EventAdapter

    private var boundVehicle: Vehicle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_detail)

        vehicleId = intent.getIntExtra("vehicleId", -1)
        if (vehicleId == -1) {
            finish()
            return
        }

        val db = AppDatabase.getInstance(this)
        val factory = ViewModelFactory(
            db.vehicleDao(),
            db.vehicleEventDao(),
            db.eventMetaDao(),
            db.userProfileDao()
        )
        viewModel = ViewModelProvider(this, factory)[LogViewModel::class.java]

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val tvIcon = findViewById<TextView>(R.id.tvIcon)
        val tvName = findViewById<TextView>(R.id.tvName)
        val tvNumber = findViewById<TextView>(R.id.tvNumber)
        val tvBrand = findViewById<TextView>(R.id.tvBrand)
        val tvModel = findViewById<TextView>(R.id.tvModel)
        val tvType = findViewById<TextView>(R.id.tvType)
        val tvFuel = findViewById<TextView>(R.id.tvFuel)
        val tvYear = findViewById<TextView>(R.id.tvYear)
        val tvOdometer = findViewById<TextView>(R.id.tvOdometer)
        val tvNotesLabel = findViewById<TextView>(R.id.tvNotesLabel)
        val tvNotes = findViewById<TextView>(R.id.tvNotes)

        val btnEdit = findViewById<ImageButton>(R.id.btnEdit)
        val btnDeleteVehicle = findViewById<ImageButton>(R.id.btnDeleteVehicle)
        val fabAddEvent = findViewById<ExtendedFloatingActionButton>(R.id.fabAddEvent)
        val recyclerEvents = findViewById<RecyclerView>(R.id.recyclerEvents)
        val tvEmptyEvents = findViewById<TextView>(R.id.tvEmptyEvents)

        eventAdapter = EventAdapter()
        eventAdapter.onEventClick = { event ->
            startActivity(
                Intent(this, EventDetailActivity::class.java).putExtra("eventId", event.id)
            )
        }
        recyclerEvents.layoutManager = LinearLayoutManager(this)
        recyclerEvents.adapter = eventAdapter

        btnEdit.setOnClickListener {
            val vehicle = boundVehicle ?: return@setOnClickListener
            val editIntent = Intent(this@VehicleDetailActivity, AddVehicleActivity::class.java).apply {
                putExtra("vehicleId", vehicle.id)
                putExtra("name", vehicle.name)
                putExtra("number", vehicle.vehicleNumber)
                putExtra("type", vehicle.type)
                putExtra("fuelType", vehicle.fuelType)
                putExtra("brand", vehicle.brand)
                putExtra("model", vehicle.model)
                putExtra("year", vehicle.year)
                putExtra("purchaseDate", vehicle.purchaseDate ?: -1L)
                putExtra("notes", vehicle.notes)
            }
            startActivity(editIntent)
        }

        btnDeleteVehicle.setOnClickListener {
            val vehicle = boundVehicle ?: return@setOnClickListener
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_vehicle)
                .setMessage(getString(R.string.delete_vehicle_confirm, vehicle.name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    viewModel.deleteVehicle(vehicle)
                    finish()
                }
                .show()
        }

        lifecycleScope.launch {
            viewModel.vehicles.collectLatest { vehicles ->
                val vehicle = vehicles.find { it.id == vehicleId }
                boundVehicle = vehicle
                if (vehicle != null) {
                    toolbar.title = vehicle.name
                    tvName.text = vehicle.name
                    tvNumber.text = vehicle.vehicleNumber
                    tvBrand.text = vehicle.brand
                    tvModel.text = vehicle.model
                    tvType.text = vehicle.type.replaceFirstChar { it.uppercase() }
                    tvFuel.text = vehicle.fuelType.replaceFirstChar { it.uppercase() }
                    tvYear.text = vehicle.year.toString()
                    tvOdometer.text = "${vehicle.odometerReading} km"

                    tvIcon.text = when (vehicle.type.lowercase()) {
                        "car" -> "🚗"
                        "bike" -> "🏍️"
                        "truck" -> "🚛"
                        "auto" -> "🛺"
                        "bus" -> "🚌"
                        else -> "🚙"
                    }

                    if (!vehicle.notes.isNullOrBlank()) {
                        tvNotesLabel.visibility = View.VISIBLE
                        tvNotes.visibility = View.VISIBLE
                        tvNotes.text = vehicle.notes
                    } else {
                        tvNotesLabel.visibility = View.GONE
                        tvNotes.visibility = View.GONE
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.events.collectLatest { events ->
                eventAdapter.submitList(events)
                if (events.isEmpty()) {
                    tvEmptyEvents.visibility = View.VISIBLE
                    recyclerEvents.visibility = View.GONE
                } else {
                    tvEmptyEvents.visibility = View.GONE
                    recyclerEvents.visibility = View.VISIBLE
                }
            }
        }

        fabAddEvent.setOnClickListener {
            val intent = Intent(this, AddEventActivity::class.java)
            intent.putExtra("vehicleId", vehicleId)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchVehicles()
        viewModel.fetchEventsForVehicle(vehicleId)
    }
}
