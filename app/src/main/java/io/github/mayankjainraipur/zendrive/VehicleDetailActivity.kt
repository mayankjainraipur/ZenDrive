package io.github.mayankjainraipur.zendrive

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VehicleDetailActivity : AppCompatActivity() {

    private lateinit var viewModel: LogViewModel
    private var vehicleId: Int = -1
    private lateinit var eventAdapter: EventAdapter
    private lateinit var documentAdapter: DocumentAdapter

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
        val btnArchive = findViewById<ImageButton>(R.id.btnArchiveVehicle)
        val fabAddEvent = findViewById<ExtendedFloatingActionButton>(R.id.fabAddEvent)
        val recyclerEvents = findViewById<RecyclerView>(R.id.recyclerEvents)
        val tvEmptyEvents = findViewById<TextView>(R.id.tvEmptyEvents)

        val recyclerDocuments = findViewById<RecyclerView>(R.id.recyclerDocuments)
        val tvEmptyDocuments = findViewById<TextView>(R.id.tvEmptyDocuments)
        val btnAddDocument = findViewById<MaterialButton>(R.id.btnAddDocument)

        eventAdapter = EventAdapter()
        eventAdapter.onEventClick = { event ->
            startActivity(
                Intent(this, EventDetailActivity::class.java).putExtra("eventId", event.id)
            )
        }
        recyclerEvents.layoutManager = LinearLayoutManager(this)
        recyclerEvents.adapter = eventAdapter

        documentAdapter = DocumentAdapter()
        documentAdapter.onDocumentLongClick = { doc -> confirmDeleteDocument(db, doc) }
        recyclerDocuments.layoutManager = LinearLayoutManager(this)
        recyclerDocuments.adapter = documentAdapter

        btnEdit.setOnClickListener {
            val vehicle = boundVehicle ?: return@setOnClickListener
            startActivity(
                Intent(this@VehicleDetailActivity, AddVehicleActivity::class.java)
                    .putExtra("vehicleId", vehicle.id)
            )
        }

        btnArchive.setOnClickListener {
            val vehicle = boundVehicle ?: return@setOnClickListener
            if (vehicle.isArchived) {
                viewModel.unarchiveVehicle(vehicle)
                Toast.makeText(this, R.string.unarchive_vehicle, Toast.LENGTH_SHORT).show()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.archive_vehicle)
                    .setMessage(getString(R.string.archive_confirm, vehicle.name))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.archive_vehicle) { _, _ ->
                        viewModel.archiveVehicle(vehicle)
                        finish()
                    }
                    .show()
            }
        }

        btnDeleteVehicle.setOnClickListener {
            val vehicle = boundVehicle ?: return@setOnClickListener
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_vehicle)
                .setMessage(getString(R.string.delete_vehicle_confirm, vehicle.name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    lifecycleScope.launch {
                        // Room cascades the document rows away; their files are ours to clear.
                        val docs = db.vehicleDocumentDao().getDocumentsForVehicle(vehicle.id)
                        viewModel.deleteVehicle(vehicle)
                        docs.forEach {
                            DocumentStore.delete(this@VehicleDetailActivity, it.localFileName)
                        }
                        // Events cascade away with the vehicle, and attachments hang off events
                        // with no foreign key to follow, so they need clearing explicitly.
                        AttachmentStore.pruneOrphans(this@VehicleDetailActivity, db)
                        finish()
                    }
                }
                .show()
        }

        lifecycleScope.launch {
            viewModel.allVehicles.collectLatest { vehicles ->
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
                    tvOdometer.text = FormatUtil.formatDistance(vehicle.odometerReading)
                    loadUsageRate()

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

                    btnArchive.contentDescription = getString(
                        if (vehicle.isArchived) R.string.unarchive_vehicle
                        else R.string.archive_vehicle
                    )
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

        findViewById<MaterialButton>(R.id.btnServiceSchedule).setOnClickListener {
            startActivity(
                Intent(this, ServiceScheduleActivity::class.java).putExtra("vehicleId", vehicleId)
            )
        }

        findViewById<MaterialButton>(R.id.btnLogOdometer).setOnClickListener {
            showLogOdometerDialog(db)
        }

        btnAddDocument.setOnClickListener {
            startActivity(
                Intent(this, AddDocumentActivity::class.java)
                    .putExtra("vehicleId", vehicleId)
            )
        }

        viewModel.selectVehicleForEvents(vehicleId)
    }

    override fun onResume() {
        super.onResume()
        loadDocuments()
    }

    private fun loadDocuments() {
        val db = AppDatabase.getInstance(this)
        val tvEmptyDocuments = findViewById<TextView>(R.id.tvEmptyDocuments)
        val recyclerDocuments = findViewById<RecyclerView>(R.id.recyclerDocuments)

        lifecycleScope.launch {
            val docs = db.vehicleDocumentDao().getDocumentsForVehicle(vehicleId)
            documentAdapter.submitList(docs)
            if (docs.isEmpty()) {
                tvEmptyDocuments.visibility = View.VISIBLE
                recyclerDocuments.visibility = View.GONE
            } else {
                tvEmptyDocuments.visibility = View.GONE
                recyclerDocuments.visibility = View.VISIBLE
            }
        }
    }

    private fun loadUsageRate() {
        val tvUsageRate = findViewById<TextView>(R.id.tvUsageRate)
        lifecycleScope.launch {
            val stats = OdometerSync.statsFor(AppDatabase.getInstance(this@VehicleDetailActivity), vehicleId)
            val perDay = stats.perDay
            if (perDay == null) {
                tvUsageRate.visibility = View.GONE
            } else {
                tvUsageRate.visibility = View.VISIBLE
                tvUsageRate.text = getString(
                    R.string.odometer_usage_rate,
                    String.format(java.util.Locale.getDefault(), "%,.0f", perDay),
                    UserPrefs.distanceUnit
                )
            }
        }
    }

    /** Records a reading by hand, for the stretches between events. */
    private fun showLogOdometerDialog(db: AppDatabase) {
        val vehicle = boundVehicle ?: return
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.odometer_current_reading)
            setText(String.format(java.util.Locale.getDefault(), "%.0f", vehicle.odometerReading))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.odometer_log_reading)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val reading = input.text?.toString()?.toDoubleOrNull()
                if (reading == null || reading <= 0) {
                    Toast.makeText(this, R.string.odometer_must_be_positive, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                // Odometers do not run backwards, so a lower number is usually a typo — but it is
                // a legitimate correction often enough that it should not simply be refused.
                if (reading < vehicle.odometerReading) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.odometer_log_reading)
                        .setMessage(
                            getString(
                                R.string.odometer_below_current,
                                FormatUtil.formatDistance(vehicle.odometerReading)
                            )
                        )
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.save_anyway) { _, _ -> saveOdometer(db, reading) }
                        .show()
                } else {
                    saveOdometer(db, reading)
                }
            }
            .show()
    }

    private fun saveOdometer(db: AppDatabase, reading: Double) {
        lifecycleScope.launch {
            OdometerSync.recordManualReading(db, vehicleId, reading)
            Toast.makeText(this@VehicleDetailActivity, R.string.odometer_saved, Toast.LENGTH_SHORT).show()
            loadUsageRate()
        }
    }

    private fun confirmDeleteDocument(db: AppDatabase, doc: VehicleDocument) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_document)
            .setMessage(getString(R.string.delete_document_confirm, doc.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    db.vehicleDocumentDao().delete(doc)
                    DocumentStore.delete(this@VehicleDetailActivity, doc.localFileName)
                    // Takes the document's expiry reminder with it.
                    ReminderSync.reconcile(db)
                    Toast.makeText(this@VehicleDetailActivity, R.string.document_deleted, Toast.LENGTH_SHORT).show()
                    loadDocuments()
                }
            }
            .show()
    }
}