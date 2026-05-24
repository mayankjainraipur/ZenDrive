package com.example.zendrive

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.launch

class ZenDriveApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        DynamicColors.applyToActivitiesIfAvailable(this)
        database = AppDatabase.getInstance(this)
        createNotificationChannels()
        ReminderScheduler.schedule(this)
        scheduleAutoBackupIfEnabled()
    }

    private fun scheduleAutoBackupIfEnabled() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
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
