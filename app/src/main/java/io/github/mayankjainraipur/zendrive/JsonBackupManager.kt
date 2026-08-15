package io.github.mayankjainraipur.zendrive

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A backup is a zip: `backup.json`, every attached document under `documents/`, and every photo
 * or receipt under `attachments/`.
 *
 * Backups written before this change were bare JSON, so [importFromBytes] sniffs the format and
 * still accepts them — those simply carry no files.
 */
object JsonBackupManager {

    private const val ENTRY_JSON = "backup.json"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun getAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    suspend fun exportToJson(db: AppDatabase, context: Context): String =
        withContext(Dispatchers.IO) {
            val vehicles = db.vehicleDao().getAllVehicles()
            val profile = db.userProfileDao().getProfile()

            val allEvents = mutableListOf<VehicleEvent>()
            val allMeta = mutableListOf<EventMeta>()
            val allReminders = mutableListOf<Reminder>()
            val allDocuments = mutableListOf<VehicleDocument>()
            val allOdometerLogs = mutableListOf<OdometerLog>()

            for (v in vehicles) {
                val events = db.vehicleEventDao().getEventsForVehicle(v.id)
                allEvents.addAll(events)
                for (e in events) {
                    allMeta.addAll(db.eventMetaDao().getMetaForEvent(e.id))
                }
                // Only reminders the user actually wrote. Generated ones are derived from event
                // due dates and document expiries, which are themselves in this bundle, so the
                // reconciler rebuilds them on the far side. Carrying them would duplicate every
                // one of them, since their sourceId points at row ids that change on import.
                allReminders.addAll(
                    db.reminderDao().getRemindersForVehicle(v.id).filterNot { it.isGenerated }
                )
                allDocuments.addAll(db.vehicleDocumentDao().getDocumentsForVehicle(v.id))
                // Same reasoning as reminders: event-derived readings are rebuilt from the events
                // in this bundle, so carrying them would duplicate every one.
                allOdometerLogs.addAll(
                    db.odometerLogDao().getForVehicle(v.id).filterNot { it.isDerived }
                )
            }

            val bundle = BackupBundle(
                schemaVersion = 7,
                appVersion = getAppVersion(context),
                exportedAt = System.currentTimeMillis(),
                profile = profile?.let { BackupProfile.fromEntity(it) },
                vehicles = vehicles.map { BackupVehicle.fromEntity(it) },
                events = allEvents.map { BackupEvent.fromEntity(it) },
                eventMeta = allMeta.map { BackupEventMeta.fromEntity(it) },
                reminders = allReminders.map { BackupReminder.fromEntity(it) },
                documents = allDocuments.map { BackupDocument.fromEntity(it) },
                odometerLogs = allOdometerLogs.map { BackupOdometerLog.fromEntity(it) },
                attachments = db.attachmentDao().getAll()
                    .filter { it.ownerType == Attachment.OWNER_EVENT }
                    .map { BackupAttachment.fromEntity(it) }
            )

            json.encodeToString(BackupBundle.serializer(), bundle)
        }

    /** Packs the bundle and every document copy into a single archive. */
    suspend fun exportToBytes(db: AppDatabase, context: Context): ByteArray =
        withContext(Dispatchers.IO) {
            val jsonString = exportToJson(db, context)
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(ENTRY_JSON))
                zip.write(jsonString.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                for (name in db.vehicleDocumentDao().getAllLocalFileNames()) {
                    val file = DocumentStore.fileFor(context, name)
                    if (!file.exists()) continue
                    zip.putNextEntry(ZipEntry(DocumentStore.ARCHIVE_PREFIX + name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }

                for (name in db.attachmentDao().getAllLocalFileNames()) {
                    val file = AttachmentStore.fileFor(context, name)
                    if (!file.exists()) continue
                    zip.putNextEntry(ZipEntry(AttachmentStore.ARCHIVE_PREFIX + name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            out.toByteArray()
        }

    /** Accepts either the current archive or a legacy bare-JSON backup. */
    suspend fun importFromBytes(db: AppDatabase, context: Context, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            try {
                if (isZip(bytes)) {
                    val jsonString = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                        var found: String? = null
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (entry.name == ENTRY_JSON) {
                                found = zip.readBytes().toString(Charsets.UTF_8)
                            } else {
                                // Each entry belongs to exactly one bucket, and its bytes can only
                                // be read once — so decide first, then read.
                                val documentName = DocumentStore.sanitizeArchiveEntry(entry.name)
                                val attachmentName = AttachmentStore.sanitizeArchiveEntry(entry.name)
                                when {
                                    documentName != null ->
                                        DocumentStore.writeRestored(context, documentName, zip.readBytes())
                                    attachmentName != null ->
                                        AttachmentStore.writeRestored(context, attachmentName, zip.readBytes())
                                }
                            }
                            zip.closeEntry()
                        }
                        found
                    }
                    importFromJson(
                        db,
                        jsonString ?: throw IllegalStateException("Backup archive has no $ENTRY_JSON")
                    )
                } else {
                    importFromJson(db, bytes.toString(Charsets.UTF_8))
                }
                // Derived rows are deliberately absent from the bundle; rebuild them from the
                // events and documents that just landed, rather than waiting for the daily worker.
                ReminderSync.reconcile(db)
                OdometerSync.reconcile(db)
            } finally {
                // Clears both the documents the restore replaced and any files it failed to claim.
                DocumentStore.pruneOrphans(context, db)
                AttachmentStore.pruneOrphans(context, db)
            }
        }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    suspend fun importFromJson(db: AppDatabase, jsonString: String) =
        withContext(Dispatchers.IO) {
            val bundle = json.decodeFromString(BackupBundle.serializer(), jsonString)

            require(bundle.schemaVersion in 1..7) {
                "Unsupported backup schema version: ${bundle.schemaVersion}"
            }

            db.withTransaction {
                val vehicleDao = db.vehicleDao()
                val eventDao = db.vehicleEventDao()
                val metaDao = db.eventMetaDao()
                val reminderDao = db.reminderDao()
                val documentDao = db.vehicleDocumentDao()
                val odometerDao = db.odometerLogDao()
                val attachmentDao = db.attachmentDao()
                val profileDao = db.userProfileDao()

                val existingVehicles = vehicleDao.getAllVehicles()
                for (v in existingVehicles) {
                    eventDao.deleteAllEventsForVehicle(v.id)
                    reminderDao.deleteAllForVehicle(v.id)
                    documentDao.deleteAllForVehicle(v.id)
                    odometerDao.deleteAllForVehicle(v.id)
                    vehicleDao.deleteVehicle(v)
                }

                // oldVehicleId -> newVehicleId
                val vehicleIdMap = mutableMapOf<Int, Int>()
                for (bv in bundle.vehicles) {
                    val newId = vehicleDao.insertVehicle(bv.toEntity())
                    vehicleIdMap[bv.originalId] = newId.toInt()
                }

                // oldEventId -> newEventId
                val eventIdMap = mutableMapOf<Int, Int>()
                for (be in bundle.events) {
                    val newVehicleId = vehicleIdMap[be.vehicleOriginalId] ?: continue
                    val newId = eventDao.insertEvent(be.toEntity(newVehicleId))
                    eventIdMap[be.originalId] = newId.toInt()
                }

                for (bm in bundle.eventMeta) {
                    val newEventId = eventIdMap[bm.eventOriginalId] ?: continue
                    metaDao.insertMeta(bm.toEntity(newEventId))
                }

                for (br in bundle.reminders) {
                    val newVehicleId = vehicleIdMap[br.vehicleOriginalId] ?: continue
                    val newEventId = br.eventOriginalId?.let { eventIdMap[it] }
                    reminderDao.insert(br.toEntity(newVehicleId, newEventId))
                }

                for (bd in bundle.documents) {
                    val newVehicleId = vehicleIdMap[bd.vehicleOriginalId] ?: continue
                    documentDao.insert(bd.toEntity(newVehicleId))
                }

                for (ba in bundle.attachments) {
                    val newEventId = eventIdMap[ba.eventOriginalId] ?: continue
                    attachmentDao.insert(ba.toEntity(newEventId))
                }

                for (bo in bundle.odometerLogs) {
                    val newVehicleId = vehicleIdMap[bo.vehicleOriginalId] ?: continue
                    odometerDao.insert(bo.toEntity(newVehicleId))
                }

                bundle.profile?.let { bp ->
                    val restored = bp.toEntity().copy(
                        lastRestoreAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    profileDao.upsert(restored)
                }
            }
        }

    suspend fun exportToUri(db: AppDatabase, context: Context, uri: Uri) =
        withContext(Dispatchers.IO) {
            val logDao = db.backupRestoreLogDao()
            val logId = logDao.insert(
                BackupRestoreLog(
                    operationType = "backup",
                    startedAt = System.currentTimeMillis(),
                    status = "in_progress",
                    clientAppVersion = getAppVersion(context)
                )
            )
            try {
                val bytes = exportToBytes(db, context)

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                } ?: throw IllegalStateException("Cannot open output stream for URI")

                logDao.insert(
                    BackupRestoreLog(
                        id = logId.toInt(),
                        operationType = "backup",
                        startedAt = logDao.getById(logId.toInt())?.startedAt
                            ?: System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis(),
                        status = "success",
                        bytesProcessed = bytes.size.toLong(),
                        clientAppVersion = getAppVersion(context)
                    )
                )
            } catch (e: Exception) {
                logDao.insert(
                    BackupRestoreLog(
                        id = logId.toInt(),
                        operationType = "backup",
                        startedAt = logDao.getById(logId.toInt())?.startedAt
                            ?: System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis(),
                        status = "failed",
                        errorMessage = e.message,
                        clientAppVersion = getAppVersion(context)
                    )
                )
                throw e
            }
        }

    suspend fun importFromUri(db: AppDatabase, context: Context, uri: Uri) =
        withContext(Dispatchers.IO) {
            val logDao = db.backupRestoreLogDao()
            val logId = logDao.insert(
                BackupRestoreLog(
                    operationType = "restore",
                    startedAt = System.currentTimeMillis(),
                    status = "in_progress",
                    clientAppVersion = getAppVersion(context)
                )
            )
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Cannot open input stream for URI")

                importFromBytes(db, context, bytes)

                logDao.insert(
                    BackupRestoreLog(
                        id = logId.toInt(),
                        operationType = "restore",
                        startedAt = logDao.getById(logId.toInt())?.startedAt
                            ?: System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis(),
                        status = "success",
                        bytesProcessed = bytes.size.toLong(),
                        clientAppVersion = getAppVersion(context)
                    )
                )
            } catch (e: Exception) {
                logDao.insert(
                    BackupRestoreLog(
                        id = logId.toInt(),
                        operationType = "restore",
                        startedAt = logDao.getById(logId.toInt())?.startedAt
                            ?: System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis(),
                        status = "failed",
                        errorMessage = e.message,
                        clientAppVersion = getAppVersion(context)
                    )
                )
                throw e
            }
        }
}