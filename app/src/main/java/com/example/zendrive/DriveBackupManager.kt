package com.example.zendrive

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Collections

data class DriveBackupInfo(
    val fileId: String,
    val name: String,
    val createdTime: Long,
    val size: Long
)

object DriveBackupManager {

    private const val BACKUP_MIME_TYPE = "application/json"
    private const val BACKUP_FILE_PREFIX = "zendrive_backup_"
    private val DRIVE_SCOPE = Scope(DriveScopes.DRIVE_APPDATA)

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(DRIVE_SCOPE)
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun getCredential(context: Context): GoogleAccountCredential? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account
        return credential
    }

    private fun buildDriveService(credential: GoogleAccountCredential): Drive {
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("ZenDrive")
            .build()
    }

    private fun getAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    suspend fun uploadBackup(context: Context, db: AppDatabase): String =
        withContext(Dispatchers.IO) {
            val credential = getCredential(context)
                ?: throw IllegalStateException("Not signed in to Google")

            val logDao = db.backupRestoreLogDao()
            val logId = logDao.insert(
                BackupRestoreLog(
                    operationType = "drive_backup",
                    startedAt = System.currentTimeMillis(),
                    status = "in_progress",
                    clientAppVersion = getAppVersion(context)
                )
            )

            try {
                val jsonString = JsonBackupManager.exportToJson(db, context)
                val bytes = jsonString.toByteArray(Charsets.UTF_8)

                val driveService = buildDriveService(credential)
                val timestamp = System.currentTimeMillis()
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = "${BACKUP_FILE_PREFIX}${timestamp}.json"
                    parents = listOf("appDataFolder")
                }

                val mediaContent = ByteArrayContent(BACKUP_MIME_TYPE, bytes)
                val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id,name,createdTime,size")
                    .execute()

                val now = System.currentTimeMillis()
                logDao.insert(
                    BackupRestoreLog(
                        id = logId.toInt(),
                        operationType = "drive_backup",
                        startedAt = logDao.getById(logId.toInt())?.startedAt ?: now,
                        completedAt = now,
                        status = "success",
                        driveFileId = uploadedFile.id,
                        bytesProcessed = bytes.size.toLong(),
                        clientAppVersion = getAppVersion(context)
                    )
                )

                val profileDao = db.userProfileDao()
                val profile = profileDao.getProfile()
                if (profile != null) {
                    profileDao.upsert(
                        profile.copy(
                            lastBackupAt = now,
                            driveAccountEmail = GoogleSignIn.getLastSignedInAccount(context)?.email
                                ?: profile.driveAccountEmail,
                            updatedAt = now
                        )
                    )
                }

                uploadedFile.id
            } catch (e: Exception) {
                val startedAt = logDao.getById(logId.toInt())?.startedAt
                    ?: System.currentTimeMillis()
                logDao.insert(
                    BackupRestoreLog(
                        id = logId.toInt(),
                        operationType = "drive_backup",
                        startedAt = startedAt,
                        completedAt = System.currentTimeMillis(),
                        status = "failed",
                        errorMessage = e.message,
                        clientAppVersion = getAppVersion(context)
                    )
                )
                throw e
            }
        }

    suspend fun listBackups(credential: GoogleAccountCredential): List<DriveBackupInfo> =
        withContext(Dispatchers.IO) {
            val driveService = buildDriveService(credential)
            val result = driveService.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id,name,createdTime,size)")
                .setOrderBy("createdTime desc")
                .setPageSize(20)
                .execute()

            result.files?.map { file ->
                DriveBackupInfo(
                    fileId = file.id,
                    name = file.name ?: "unknown",
                    createdTime = file.createdTime?.value ?: 0L,
                    size = file.getSize()?.toLong() ?: 0L
                )
            } ?: emptyList()
        }

    suspend fun restoreFromDrive(context: Context, db: AppDatabase, fileId: String) =
        withContext(Dispatchers.IO) {
            val credential = getCredential(context)
                ?: throw IllegalStateException("Not signed in to Google")

            val logDao = db.backupRestoreLogDao()
            val logId = logDao.insert(
                BackupRestoreLog(
                    operationType = "drive_restore",
                    startedAt = System.currentTimeMillis(),
                    status = "in_progress",
                    driveFileId = fileId,
                    clientAppVersion = getAppVersion(context)
                )
            )

            try {
                val driveService = buildDriveService(credential)
                val outputStream = ByteArrayOutputStream()
                driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                val bytes = outputStream.toByteArray()
                val jsonString = bytes.toString(Charsets.UTF_8)

                JsonBackupManager.importFromJson(db, jsonString)

                val now = System.currentTimeMillis()
                logDao.insert(
                    BackupRestoreLog(
                        id = logId.toInt(),
                        operationType = "drive_restore",
                        startedAt = logDao.getById(logId.toInt())?.startedAt ?: now,
                        completedAt = now,
                        status = "success",
                        driveFileId = fileId,
                        bytesProcessed = bytes.size.toLong(),
                        clientAppVersion = getAppVersion(context)
                    )
                )

                val profileDao = db.userProfileDao()
                val profile = profileDao.getProfile()
                if (profile != null) {
                    profileDao.upsert(
                        profile.copy(
                            lastRestoreAt = now,
                            driveAccountEmail = GoogleSignIn.getLastSignedInAccount(context)?.email
                                ?: profile.driveAccountEmail,
                            updatedAt = now
                        )
                    )
                }
            } catch (e: Exception) {
                val startedAt = logDao.getById(logId.toInt())?.startedAt
                    ?: System.currentTimeMillis()
                logDao.insert(
                    BackupRestoreLog(
                        id = logId.toInt(),
                        operationType = "drive_restore",
                        startedAt = startedAt,
                        completedAt = System.currentTimeMillis(),
                        status = "failed",
                        driveFileId = fileId,
                        errorMessage = e.message,
                        clientAppVersion = getAppVersion(context)
                    )
                )
                throw e
            }
        }

    suspend fun deleteOldBackups(credential: GoogleAccountCredential, keepLast: Int = 5) =
        withContext(Dispatchers.IO) {
            val allBackups = listBackups(credential)
            if (allBackups.size <= keepLast) return@withContext

            val driveService = buildDriveService(credential)
            val toDelete = allBackups.drop(keepLast)
            for (backup in toDelete) {
                try {
                    driveService.files().delete(backup.fileId).execute()
                } catch (_: Exception) {
                    // best-effort cleanup
                }
            }
        }
}
