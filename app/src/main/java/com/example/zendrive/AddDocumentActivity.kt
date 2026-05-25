package com.example.zendrive

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddDocumentActivity : AppCompatActivity() {

    private var vehicleId: Int = -1
    private var editingDocId: Int = -1
    private var selectedUri: Uri? = null
    private var selectedFileName: String? = null
    private var selectedMimeType: String? = null
    private var selectedFileSize: Long? = null
    private var expiryDateMillis: Long? = null
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private lateinit var toolbar: MaterialToolbar
    private lateinit var actvDocumentType: AutoCompleteTextView
    private lateinit var etTitle: TextInputEditText
    private lateinit var btnChooseFile: MaterialButton
    private lateinit var tvSelectedFile: TextView
    private lateinit var etExpiryDate: TextInputEditText
    private lateinit var etNotes: TextInputEditText
    private lateinit var btnSaveDocument: MaterialButton

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            selectedUri = uri
            selectedMimeType = contentResolver.getType(uri)
            queryFileDetails(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_document)

        val db = AppDatabase.getInstance(this)

        toolbar = findViewById(R.id.toolbar)
        actvDocumentType = findViewById(R.id.actvDocumentType)
        etTitle = findViewById(R.id.etTitle)
        btnChooseFile = findViewById(R.id.btnChooseFile)
        tvSelectedFile = findViewById(R.id.tvSelectedFile)
        etExpiryDate = findViewById(R.id.etExpiryDate)
        etNotes = findViewById(R.id.etNotes)
        btnSaveDocument = findViewById(R.id.btnSaveDocument)

        toolbar.setNavigationOnClickListener { finish() }

        val docTypes = resources.getStringArray(R.array.document_types)
        actvDocumentType.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, docTypes)
        )

        editingDocId = intent.getIntExtra("documentId", -1)

        btnChooseFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("*/*"))
        }

        etExpiryDate.setOnClickListener { showDatePicker() }

        btnSaveDocument.setOnClickListener { saveDocument(db) }

        if (editingDocId != -1) {
            toolbar.title = getString(R.string.edit_document)
            btnSaveDocument.isEnabled = false
            lifecycleScope.launch {
                val doc = db.vehicleDocumentDao().getById(editingDocId)
                if (doc == null) {
                    finish()
                    return@launch
                }
                vehicleId = doc.vehicleId
                selectedUri = Uri.parse(doc.storageUri)
                selectedFileName = doc.fileName
                selectedMimeType = doc.mimeType
                selectedFileSize = doc.fileSizeBytes
                expiryDateMillis = doc.expiresAt

                val vehicle = db.vehicleDao().getVehicleById(vehicleId)
                if (vehicle != null) toolbar.subtitle = vehicle.name

                actvDocumentType.setText(
                    doc.documentType.replaceFirstChar { c ->
                        if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
                    },
                    false
                )
                etTitle.setText(doc.title)
                tvSelectedFile.text = getString(R.string.file_selected, doc.fileName)
                tvSelectedFile.visibility = View.VISIBLE
                if (doc.expiresAt != null) {
                    etExpiryDate.setText(dateFormat.format(doc.expiresAt))
                }
                etNotes.setText(doc.notes.orEmpty())
                btnSaveDocument.isEnabled = true
            }
        } else {
            vehicleId = intent.getIntExtra("vehicleId", -1)
            if (vehicleId == -1) {
                finish()
                return
            }
            toolbar.title = getString(R.string.add_document)
            lifecycleScope.launch {
                val vehicle = db.vehicleDao().getVehicleById(vehicleId)
                if (vehicle != null) toolbar.subtitle = vehicle.name
            }
        }
    }

    private fun queryFileDetails(uri: Uri) {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) selectedFileName = cursor.getString(nameIdx)
                if (sizeIdx >= 0) selectedFileSize = cursor.getLong(sizeIdx)
            }
        }
        tvSelectedFile.text = getString(R.string.file_selected, selectedFileName ?: uri.lastPathSegment)
        tvSelectedFile.visibility = View.VISIBLE
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        if (expiryDateMillis != null) cal.timeInMillis = expiryDateMillis!!
        DatePickerDialog(
            this,
            { _, year, month, day ->
                cal.set(year, month, day, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                expiryDateMillis = cal.timeInMillis
                etExpiryDate.setText(dateFormat.format(cal.time))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveDocument(db: AppDatabase) {
        val docType = actvDocumentType.text.toString().trim()
        val title = etTitle.text.toString().trim()
        val notes = etNotes.text.toString().trim()

        if (docType.isEmpty() || title.isEmpty()) {
            Toast.makeText(this, R.string.field_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedUri == null) {
            Toast.makeText(this, R.string.no_file_selected, Toast.LENGTH_SHORT).show()
            return
        }

        if (vehicleId == -1) return

        val now = System.currentTimeMillis()

        lifecycleScope.launch {
            if (editingDocId != -1) {
                val existing = db.vehicleDocumentDao().getById(editingDocId) ?: return@launch
                val updated = existing.copy(
                    title = title,
                    documentType = docType.lowercase(Locale.getDefault()),
                    fileName = selectedFileName ?: "",
                    mimeType = selectedMimeType,
                    storageUri = selectedUri.toString(),
                    fileSizeBytes = selectedFileSize,
                    expiresAt = expiryDateMillis,
                    notes = notes.ifEmpty { null },
                    updatedAt = now
                )
                db.vehicleDocumentDao().update(updated)
                Toast.makeText(this@AddDocumentActivity, R.string.document_updated, Toast.LENGTH_SHORT).show()
            } else {
                val doc = VehicleDocument(
                    vehicleId = vehicleId,
                    title = title,
                    documentType = docType.lowercase(Locale.getDefault()),
                    fileName = selectedFileName ?: "",
                    mimeType = selectedMimeType,
                    storageUri = selectedUri.toString(),
                    fileSizeBytes = selectedFileSize,
                    expiresAt = expiryDateMillis,
                    notes = notes.ifEmpty { null },
                    createdAt = now,
                    updatedAt = now
                )
                db.vehicleDocumentDao().insert(doc)
                Toast.makeText(this@AddDocumentActivity, R.string.document_saved, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
