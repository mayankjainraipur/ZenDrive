package io.github.mayankjainraipur.zendrive

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Handles the Done and Snooze buttons on a reminder notification. */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, -1)
        if (reminderId == -1) return

        val action = intent.action ?: return
        val appContext = context.applicationContext

        // The row update outlives onReceive, so hold the broadcast open for it.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(appContext).reminderDao()
                val reminder = dao.getById(reminderId) ?: return@launch
                val now = System.currentTimeMillis()

                when (action) {
                    ACTION_COMPLETE -> dao.update(
                        reminder.copy(isCompleted = true, completedAt = now, updatedAt = now)
                    )
                    // Snooze sets notifyAt, not dueAt: the insurance still expires when it
                    // expires. Moving dueAt would also make the reconciler treat a generated
                    // reminder as out of step with its source and reset it.
                    ACTION_SNOOZE -> dao.update(
                        reminder.copy(notifyAt = now + TimeUnit.DAYS.toMillis(1), updatedAt = now)
                    )
                }
                NotificationManagerCompat.from(appContext).cancel(reminderId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "io.github.mayankjainraipur.zendrive.REMINDER_COMPLETE"
        const val ACTION_SNOOZE = "io.github.mayankjainraipur.zendrive.REMINDER_SNOOZE"
        private const val EXTRA_REMINDER_ID = "reminderId"

        fun pendingIntent(context: Context, reminderId: Int, action: String): PendingIntent {
            val intent = Intent(context, ReminderActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_REMINDER_ID, reminderId)
            }
            return PendingIntent.getBroadcast(
                context,
                // Distinct per reminder *and* per action, or the two buttons collide.
                reminderId * 2 + if (action == ACTION_SNOOZE) 1 else 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
