package io.github.mayankjainraipur.zendrive

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.launch

class ZenDriveApp : Application() {

    lateinit var database: AppDatabase
        private set

    /**
     * For writes that must not die with the screen that started them — changing the theme
     * recreates the activity, which would cancel a `lifecycleScope` job mid-write.
     */
    val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Read the cached theme and apply it before any activity inflates, or the first frame
        // paints the wrong palette.
        UserPrefs.init(this)
        UserPrefs.applyTheme()
        DynamicColors.applyToActivitiesIfAvailable(this)
        database = AppDatabase.getInstance(this)
        createNotificationChannels()
        ReminderScheduler.schedule(this)
        scheduleAutoBackupIfEnabled()
        adoptLegacyDocuments()
        mirrorProfilePrefs()
    }

    /** Keeps [UserPrefs] in step with the profile row, including after a restore. */
    private fun mirrorProfilePrefs() {
        appScope.launch {
            database.userProfileDao().observeProfile().collect { profile ->
                if (profile != null) UserPrefs.mirror(profile)
            }
        }
    }

    /** Rescues documents saved before files were copied in, while their URIs still resolve. */
    private fun adoptLegacyDocuments() {
        appScope.launch {
            runCatching { DocumentStore.adoptLegacyDocuments(this@ZenDriveApp, database) }
        }
    }

    private fun scheduleAutoBackupIfEnabled() {
        appScope.launch {
            val profile = database.userProfileDao().getProfile()
            if (profile?.backupEnabled == true) {
                DriveAutoBackupWorker.schedule(this@ZenDriveApp)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Vehicle service, insurance, and document expiry reminders"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_REMINDERS = "zendrive_reminders"

        lateinit var instance: ZenDriveApp
            private set
    }
}