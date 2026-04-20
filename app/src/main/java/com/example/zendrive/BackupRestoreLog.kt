package com.example.zendrive

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Audit trail for Google Drive backup and restore operations.
 */
@Entity(tableName = "backup_restore_log")
data class BackupRestoreLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** backup or restore */
    val operationType: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    /** success, failed, cancelled */
    val status: String,
    val driveFileId: String? = null,
    val bytesProcessed: Long? = null,
    val errorMessage: String? = null,
    /** App version name at operation time (e.g. from PackageManager) */
    val clientAppVersion: String? = null
)
