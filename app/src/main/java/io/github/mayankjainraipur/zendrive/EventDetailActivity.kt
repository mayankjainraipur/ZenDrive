package io.github.mayankjainraipur.zendrive

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventDetailActivity : AppCompatActivity() {

    private var eventId: Int = -1
    private val dateFormat get() = SimpleDateFormat(UserPrefs.dateFormatPattern, Locale.getDefault())
    private var currencyCode: String = "INR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        eventId = intent.getIntExtra("eventId", -1)
        if (eventId == -1) {
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_edit_event) {
                startActivity(
                    Intent(this, AddEventActivity::class.java).putExtra("eventId", eventId)
                )
                true
            } else {
                false
            }
        }

        setupAttachmentButtons()
    }

    // ─── Attachments ─────────────────────────────────────────────────────────

    /** Where the camera writes; only copied into the owned bucket once a capture succeeds. */
    private var pendingCaptureFile: java.io.File? = null

    private val pickAttachmentLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) attachFrom(uri, null) }

    private val takePhotoLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (success && file != null) {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            attachFrom(uri, file.name)
        } else {
            // Cancelled or failed — don't leave the empty capture behind in the cache.
            file?.delete()
        }
    }

    override fun onResume() {
        super.onResume()
        loadEvent()
        loadAttachments()
    }

    private fun setupAttachmentButtons() {
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPickPhoto)
            .setOnClickListener {
                pickAttachmentLauncher.launch(arrayOf("image/*", "application/pdf"))
            }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTakePhoto)
            .setOnClickListener {
                val dir = java.io.File(cacheDir, "captures").apply { mkdirs() }
                val file = java.io.File(dir, "capture_${System.currentTimeMillis()}.jpg")
                pendingCaptureFile = file
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this, "$packageName.fileprovider", file
                )
                try {
                    takePhotoLauncher.launch(uri)
                } catch (_: Exception) {
                    pendingCaptureFile = null
                    file.delete()
                    android.widget.Toast.makeText(
                        this, R.string.attachment_no_camera, android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun attachFrom(uri: android.net.Uri, displayName: String?) {
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            try {
                val stored = AttachmentStore.copyIn(this@EventDetailActivity, uri, displayName)
                db.attachmentDao().insert(
                    Attachment(
                        ownerType = Attachment.OWNER_EVENT,
                        ownerId = eventId,
                        localFileName = stored,
                        mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                    )
                )
                // The capture in cache has been copied into the owned bucket; drop the original.
                pendingCaptureFile?.delete()
                pendingCaptureFile = null
                android.widget.Toast.makeText(
                    this@EventDetailActivity, R.string.attachment_added,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                loadAttachments()
            } catch (_: Exception) {
                android.widget.Toast.makeText(
                    this@EventDetailActivity, R.string.attachment_failed,
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadAttachments() {
        val container = findViewById<LinearLayout>(R.id.attachmentContainer) ?: return
        val empty = findViewById<TextView>(R.id.tvNoAttachments)
        val db = AppDatabase.getInstance(this)

        lifecycleScope.launch {
            val attachments = db.attachmentDao()
                .getForOwner(Attachment.OWNER_EVENT, eventId)
            container.removeAllViews()
            empty.visibility = if (attachments.isEmpty()) View.VISIBLE else View.GONE

            val sizePx = (88 * resources.displayMetrics.density).toInt()
            for (attachment in attachments) {
                val thumb = android.widget.ImageView(this@EventDetailActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                        marginEnd = (8 * resources.displayMetrics.density).toInt()
                    }
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    setBackgroundResource(R.color.surface_light)
                    contentDescription = getString(R.string.attachments)
                }
                container.addView(thumb)

                // Decoding is IO plus a chunk of allocation; keep it off the main thread.
                val bitmap = decodeThumbnail(
                    AttachmentStore.fileFor(this@EventDetailActivity, attachment.localFileName),
                    sizePx
                )
                if (bitmap != null) thumb.setImageBitmap(bitmap)

                thumb.setOnClickListener {
                    runCatching {
                        startActivity(
                            AttachmentStore.viewIntent(this@EventDetailActivity, attachment)
                        )
                    }
                }
                thumb.setOnLongClickListener {
                    confirmDeleteAttachment(db, attachment)
                    true
                }
            }
        }
    }

    /** Samples down while decoding so a 12-megapixel photo never lands in memory whole. */
    private suspend fun decodeThumbnail(file: java.io.File, targetPx: Int): android.graphics.Bitmap? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!file.exists()) return@withContext null
            runCatching {
                val bounds = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / sample > targetPx * 2 &&
                    bounds.outHeight / sample > targetPx * 2
                ) {
                    sample *= 2
                }
                android.graphics.BitmapFactory.decodeFile(
                    file.absolutePath,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }.getOrNull()
        }

    private fun confirmDeleteAttachment(db: AppDatabase, attachment: Attachment) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setMessage(R.string.attachment_delete_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    db.attachmentDao().delete(attachment)
                    AttachmentStore.delete(this@EventDetailActivity, attachment.localFileName)
                    android.widget.Toast.makeText(
                        this@EventDetailActivity, R.string.attachment_deleted,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    loadAttachments()
                }
            }
            .show()
    }

    private fun loadEvent() {
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            val profile = db.userProfileDao().getProfile()
            currencyCode = profile?.preferredCurrencyCode?.takeIf { it.isNotBlank() } ?: "INR"

            val event = db.vehicleEventDao().getEventById(eventId) ?: run {
                finish()
                return@launch
            }

            val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
            toolbar.title = event.title

            findViewById<TextView>(R.id.tvEventTypeBadge).text = event.eventType.uppercase()
            findViewById<TextView>(R.id.tvEventDate).text = dateFormat.format(Date(event.date))
            findViewById<TextView>(R.id.tvEventTitle).text = event.title

            val tvDescLabel = findViewById<TextView>(R.id.tvDescriptionLabel)
            val tvDesc = findViewById<TextView>(R.id.tvDescription)
            if (!event.description.isNullOrBlank()) {
                tvDescLabel.visibility = View.VISIBLE
                tvDesc.visibility = View.VISIBLE
                tvDesc.text = event.description
            } else {
                tvDescLabel.visibility = View.GONE
                tvDesc.visibility = View.GONE
            }

            val rowCost = findViewById<LinearLayout>(R.id.rowCost)
            val tvCost = findViewById<TextView>(R.id.tvCost)
            if (event.cost != null && event.cost > 0) {
                rowCost.visibility = View.VISIBLE
                tvCost.text = "$currencyCode ${String.format(Locale.getDefault(), "%,.0f", event.cost)}"
            } else {
                rowCost.visibility = View.GONE
            }

            val rowOdometer = findViewById<LinearLayout>(R.id.rowOdometer)
            val tvOdometer = findViewById<TextView>(R.id.tvOdometer)
            if (event.odometer != null && event.odometer > 0) {
                rowOdometer.visibility = View.VISIBLE
                tvOdometer.text = "${String.format(Locale.getDefault(), "%,.0f", event.odometer)} km"
            } else {
                rowOdometer.visibility = View.GONE
            }

            val rowNextDue = findViewById<LinearLayout>(R.id.rowNextDue)
            val tvNextDue = findViewById<TextView>(R.id.tvNextDue)
            if (event.nextDueDate != null) {
                rowNextDue.visibility = View.VISIBLE
                tvNextDue.text = dateFormat.format(Date(event.nextDueDate))
            } else {
                rowNextDue.visibility = View.GONE
            }

            val metaList = db.eventMetaDao().getMetaForEvent(eventId)
            val header = findViewById<TextView>(R.id.tvExtraFieldsHeader)
            val llMeta = findViewById<LinearLayout>(R.id.llMetaDetail)
            llMeta.removeAllViews()
            if (metaList.isNotEmpty()) {
                header.visibility = View.VISIBLE
                llMeta.visibility = View.VISIBLE
                val gap = (10 * resources.displayMetrics.density).toInt()
                metaList.forEach { m ->
                    val col = LinearLayout(this@EventDetailActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = gap }
                    }
                    val k = TextView(this@EventDetailActivity).apply {
                        text = m.key
                        setTextColor(ContextCompat.getColor(this@EventDetailActivity, R.color.text_hint))
                        textSize = 12f
                    }
                    val v = TextView(this@EventDetailActivity).apply {
                        text = m.value
                        setTextColor(ContextCompat.getColor(this@EventDetailActivity, R.color.text_primary))
                        textSize = 15f
                    }
                    col.addView(k)
                    col.addView(v)
                    llMeta.addView(col)
                }
            } else {
                header.visibility = View.GONE
                llMeta.visibility = View.GONE
            }
        }
    }
}