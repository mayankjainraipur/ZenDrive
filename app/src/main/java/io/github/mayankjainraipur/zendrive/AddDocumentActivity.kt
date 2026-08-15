package io.github.mayankjainraipur.zendrive

import android.app.DatePickerDialog
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

    /** Sentinel for a document that belongs to the owner rather than any vehicle. */
    private var vehicleId: Int = PERSONAL
    private var editingDocId: Int = -1

    /** Copy taken at pick time, not yet referenced by any row. */
    private var stagedLocalFileName: String? = null

    /** The copy already committed to the row being edited, if any. */
    private var existingLocalFileName: String? = null

    private var originalUriString: String? = null
    private var selectedFileName: String? = null
    private var selectedMimeType: String? = null
    private var selectedFileSize: Long? = null
    private var expiryDateMillis: Long? = null
    private var selectedPreset: DocumentPreset? = null
    private var committed = false
    private val dateFormat get() = SimpleDateFormat(UserPrefs.dateFormatPattern, Locale.getDefault())

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
        if (uri != null) copySelectedFile(uri)
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

        val presets = DocumentPreset.entries
        actvDocumentType.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                presets.map { getString(it.labelRes) }
            )
        )

        // Choosing a type offers the expiry that type normally runs on, so the common case needs
        // no arithmetic. Only ever fills a blank field — a date already entered is left alone.
        actvDocumentType.setOnItemClickListener { _, _, position, _ ->
            selectedPreset = presets[position]
            val suggested = selectedPreset?.suggestedExpiry()
            if (suggested != null && expiryDateMillis == null) {
                expiryDateMillis = suggested
                etExpiryDate.setText(dateFormat.format(suggested))
                Toast.makeText(this, R.string.doc_expiry_suggested, Toast.LENGTH_SHORT).show()
            }
        }

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
                vehicleId = doc.vehicleId ?: PERSONAL
                existingLocalFileName = doc.localFileName
                originalUriString = doc.storageUri
                selectedFileName = doc.fileName
                selectedMimeType = doc.mimeType
                selectedFileSize = doc.fileSizeBytes
                expiryDateMillis = doc.expiresAt

                val vehicle = db.vehicleDao().getVehicleById(vehicleId)
                if (vehicle != null) toolbar.subtitle = vehicle.name

                // Map the stored key back to its label; fall back to the raw value for rows
                // saved before presets existed.
                selectedPreset = DocumentPreset.fromKey(doc.documentType)
                actvDocumentType.setText(
                    selectedPreset?.let { getString(it.labelRes) }
                        ?: doc.documentType.replaceFirstChar { c ->
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
                restoreStagedFile(savedInstanceState)
                btnSaveDocument.isEnabled = true
            }
        } else {
            vehicleId = intent.getIntExtra("vehicleId", PERSONAL)
            toolbar.title = getString(R.string.add_document)
            restoreStagedFile(savedInstanceState)
            lifecycleScope.launch {
                val vehicle = db.vehicleDao().getVehicleById(vehicleId)
                if (vehicle != null) toolbar.subtitle = vehicle.name
            }
        }
    }

    /** Takes our own copy straight away, so the record stops depending on the picked URI. */
    private fun copySelectedFile(uri: Uri) {
        lifecycleScope.launch {
            val displayName = queryDisplayName(uri)
            val copied = try {
                DocumentStore.copyIn(this@AddDocumentActivity, uri, displayName)
            } catch (_: Exception) {
                Toast.makeText(
                    this@AddDocumentActivity, R.string.document_copy_failed, Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            // A second pick replaces the first; drop the copy that lost.
            DocumentStore.delete(this@AddDocumentActivity, stagedLocalFileName)
            stagedLocalFileName = copied
            originalUriString = uri.toString()
            selectedFileName = displayName ?: uri.lastPathSegment.orEmpty()
            selectedMimeType = contentResolver.getType(uri)
            selectedFileSize = DocumentStore.fileFor(this@AddDocumentActivity, copied).length()
            showSelectedFile()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) return cursor.getString(nameIdx)
            }
        }
        return null
    }

    private fun showSelectedFile() {
        tvSelectedFile.text = getString(R.string.file_selected, selectedFileName.orEmpty())
        tvSelectedFile.visibility = View.VISIBLE
    }

    /** Re-attaches a file picked before a configuration change. */
    private fun restoreStagedFile(state: Bundle?) {
        if (state == null) return
        if (state.containsKey(KEY_EXPIRY)) expiryDateMillis = state.getLong(KEY_EXPIRY)
        val staged = state.getString(KEY_STAGED) ?: return
        stagedLocalFileName = staged
        existingLocalFileName = state.getString(KEY_EXISTING) ?: existingLocalFileName
        originalUriString = state.getString(KEY_ORIGINAL_URI) ?: originalUriString
        selectedFileName = state.getString(KEY_FILE_NAME) ?: selectedFileName
        selectedMimeType = state.getString(KEY_MIME) ?: selectedMimeType
        if (state.containsKey(KEY_SIZE)) selectedFileSize = state.getLong(KEY_SIZE)
        showSelectedFile()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_STAGED, stagedLocalFileName)
        outState.putString(KEY_EXISTING, existingLocalFileName)
        outState.putString(KEY_ORIGINAL_URI, originalUriString)
        outState.putString(KEY_FILE_NAME, selectedFileName)
        outState.putString(KEY_MIME, selectedMimeType)
        selectedFileSize?.let { outState.putLong(KEY_SIZE, it) }
        expiryDateMillis?.let { outState.putLong(KEY_EXPIRY, it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Abandoned before saving — don't leave the copy behind.
        if (!committed && isFinishing) {
            DocumentStore.delete(this, stagedLocalFileName)
        }
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
        // Store the stable preset key, not the shown label, so a wording change never orphans
        // existing rows.
        val docType = selectedPreset?.key ?: actvDocumentType.text.toString().trim()
        val title = etTitle.text.toString().trim()
        val notes = etNotes.text.toString().trim()

        if (docType.isEmpty() || title.isEmpty()) {
            Toast.makeText(this, R.string.field_required, Toast.LENGTH_SHORT).show()
            return
        }

        val localFileName = stagedLocalFileName ?: existingLocalFileName
        if (localFileName == null && originalUriString == null) {
            Toast.makeText(this, R.string.no_file_selected, Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        // PERSONAL is stored as null: the row belongs to the owner, not a vehicle.
        val ownerVehicleId = vehicleId.takeIf { it != PERSONAL }

        lifecycleScope.launch {
            if (editingDocId != -1) {
                val existing = db.vehicleDocumentDao().getById(editingDocId) ?: return@launch
                val updated = existing.copy(
                    title = title,
                    documentType = docType.lowercase(Locale.getDefault()),
                    fileName = selectedFileName ?: existing.fileName,
                    mimeType = selectedMimeType,
                    storageUri = originalUriString ?: existing.storageUri,
                    localFileName = localFileName,
                    fileSizeBytes = selectedFileSize,
                    expiresAt = expiryDateMillis,
                    notes = notes.ifEmpty { null },
                    updatedAt = now
                )
                db.vehicleDocumentDao().update(updated)
                committed = true
                // The row points at the new copy now; the one it replaced is dead weight.
                if (stagedLocalFileName != null && existingLocalFileName != stagedLocalFileName) {
                    DocumentStore.delete(this@AddDocumentActivity, existingLocalFileName)
                }
                Toast.makeText(this@AddDocumentActivity, R.string.document_updated, Toast.LENGTH_SHORT).show()
            } else {
                val doc = VehicleDocument(
                    vehicleId = ownerVehicleId,
                    title = title,
                    documentType = docType.lowercase(Locale.getDefault()),
                    fileName = selectedFileName ?: "",
                    mimeType = selectedMimeType,
                    storageUri = originalUriString.orEmpty(),
                    localFileName = localFileName,
                    fileSizeBytes = selectedFileSize,
                    expiresAt = expiryDateMillis,
                    notes = notes.ifEmpty { null },
                    createdAt = now,
                    updatedAt = now
                )
                db.vehicleDocumentDao().insert(doc)
                committed = true
                Toast.makeText(this@AddDocumentActivity, R.string.document_saved, Toast.LENGTH_SHORT).show()
            }
            // An expiry date is a promise to warn the user; derive the reminder from it.
            ReminderSync.reconcile(db)
            finish()
        }
    }

    companion object {
        /** Passed as the vehicleId extra to create a document with no vehicle. */
        const val PERSONAL = -1

        private const val KEY_STAGED = "stagedLocalFileName"
        private const val KEY_EXISTING = "existingLocalFileName"
        private const val KEY_ORIGINAL_URI = "originalUriString"
        private const val KEY_FILE_NAME = "selectedFileName"
        private const val KEY_MIME = "selectedMimeType"
        private const val KEY_SIZE = "selectedFileSize"
        private const val KEY_EXPIRY = "expiryDateMillis"
    }
}