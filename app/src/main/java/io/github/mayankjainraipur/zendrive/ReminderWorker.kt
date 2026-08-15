package io.github.mayankjainraipur.zendrive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)

        // Warn ahead of the due date — a reminder that first speaks on the day it expires is
        // just a record of being late.
        val leadMillis = TimeUnit.DAYS.toMillis(UserPrefs.reminderLeadDays.toLong())
        val cutoff = System.currentTimeMillis() + leadMillis
        val dueReminders = db.reminderDao().getDueOrOverdue(cutoff)

        if (dueReminders.isEmpty()) return Result.success()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        val notificationManager = NotificationManagerCompat.from(applicationContext)

        for (reminder in dueReminders) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                reminder.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val timing = describeTiming(reminder.dueAt)
            val body = reminder.description?.takeIf { it.isNotBlank() }
                ?.let { "$timing · $it" } ?: timing

            val notification = NotificationCompat.Builder(
                applicationContext, ZenDriveApp.CHANNEL_REMINDERS
            )
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(reminder.title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(
                    0,
                    applicationContext.getString(R.string.reminder_action_done),
                    ReminderActionReceiver.pendingIntent(
                        applicationContext, reminder.id, ReminderActionReceiver.ACTION_COMPLETE
                    )
                )
                .addAction(
                    0,
                    applicationContext.getString(R.string.reminder_action_snooze),
                    ReminderActionReceiver.pendingIntent(
                        applicationContext, reminder.id, ReminderActionReceiver.ACTION_SNOOZE
                    )
                )
                .build()

            notificationManager.notify(reminder.id, notification)
        }

        return Result.success()
    }

    /**
     * Phrased in whole days between calendar dates, not elapsed milliseconds — something due
     * this evening is "today", not "in 0 days", whatever time the worker happens to run.
     */
    private fun describeTiming(dueAt: Long): String {
        val days = daysBetween(System.currentTimeMillis(), dueAt)
        val res = applicationContext
        return when {
            days > 1 -> res.getString(R.string.reminder_due_in_days, days)
            days == 1 -> res.getString(R.string.reminder_due_tomorrow)
            days == 0 -> res.getString(R.string.reminder_due_today)
            days == -1 -> res.getString(R.string.reminder_overdue_yesterday)
            else -> res.getString(R.string.reminder_overdue_days, -days)
        }
    }

    private fun daysBetween(from: Long, to: Long): Int {
        val start = startOfDay(from)
        val end = startOfDay(to)
        return TimeUnit.MILLISECONDS.toDays(end - start).toInt()
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
