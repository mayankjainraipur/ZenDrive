package com.example.zendrive

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row profile (singleton [id] = 1). Captured once at onboarding; updated when the user edits.
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val displayName: String,
    val email: String,
    val mobileNumber: String? = null,
    /** ISO 4217 code, e.g. INR, USD */
    val preferredCurrencyCode: String,
    /** "km" or "mi" */
    val distanceUnit: String = "km",
    /** e.g. "dd MMM yyyy", "MM/dd/yyyy", "yyyy-MM-dd" */
    val dateFormatPattern: String = "dd MMM yyyy",
    /** "dark", "light", or "system" */
    val themeMode: String = "dark",
    /** Days before due date to fire reminder notification */
    val reminderLeadDays: Int = 3,
    val appLockEnabled: Boolean = false,
    val backupEnabled: Boolean = false,
    /** Epoch millis; null if backup has never completed */
    val lastBackupAt: Long? = null,
    /** Epoch millis; null if restore has never completed */
    val lastRestoreAt: Long? = null,
    /** Google account used for Drive backup/restore, if any */
    val driveAccountEmail: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
