package com.example.zendrive

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object JsonBackupManager {

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

            for (v in vehicles) {
                val events = db.vehicleEventDao().getEventsForVehicle(v.id)
                allEvents.addAll(events)
                for (e in events) {
                    allMeta.addAll(db.eventMetaDao().getMetaForEvent(e.id))
                }
                allReminders.addAll(db.reminderDao().getRemindersForVehicle(v.id))
                allDocuments.addAll(db.vehicleDocumentDao().getDocumentsForVehicle(v.id))
            }

            val bundle = BackupBundle(
                schemaVersion = 3,
                appVersion = getAppVersion(context),
                exportedAt = System.currentTimeMillis(),
                profile = profile?.let { BackupProfile.fromEntity(it) },
                vehicles = vehicles.map { BackupVehicle.fromEntity(it) },
                events = allEvents.map { BackupEvent.fromEntity(it) },
                eventMeta = allMeta.map { BackupEventMeta.fromEntity(it) },
                reminders = allReminders.map { BackupReminder.fromEntity(it) },
                documents = allDocuments.map { BackupDocument.fromEntity(it) }
            )

            json.encodeToString(BackupBundle.serializer(), bundle)
        }

    suspend fun importFromJson(db: AppDatabase, jsonString: String) =
        withContext(Dispatchers.IO) {
            val bundle = json.decodeFromString(BackupBundle.serializer(), jsonString)

            require(bundle.schemaVersion in 1..3) {
                "Unsupported backup schema version: ${bundle.schemaVersion}"
            }

            db.withTransaction {
                val vehicleDao = db.vehicleDao()
                val eventDao = db.vehicleEventDao()
                val metaDao = db.eventMetaDao()
                val reminderDao = db.reminderDao()
                val documentDao = db.vehicleDocumentDao()
                val profileDao = db.userProfileDao()

                val existingVehicles = vehicleDao.getAllVehicles()
                for (v in existingVehicles) {
                    eventDao.deleteAllEventsForVehicle(v.id)
                    reminderDao.deleteAllForVehicle(v.id)
                    documentDao.deleteAllForVehicle(v.id)
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
                val jsonString = exportToJson(db, context)
                val bytes = jsonString.toByteArray(Charsets.UTF_8)

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

                val jsonString = bytes.toString(Charsets.UTF_8)
                importFromJson(db, jsonString)

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
