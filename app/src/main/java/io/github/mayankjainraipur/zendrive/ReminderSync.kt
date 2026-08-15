package io.github.mayankjainraipur.zendrive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps a reminder in step with the event or document it came from.
 *
 * Every event can carry a nextDueDate and every document an expiresAt, but nothing used to act on
 * them — reminders were created by hand, in a separate screen, retyping a date the user had
 * already entered. This derives them instead.
 *
 * Reconciling rather than firing on write means the result is the same whichever way the data
 * arrived: a normal edit, a Drive restore, or a backup imported from another phone.
 */
object ReminderSync {

    suspend fun reconcile(db: AppDatabase) = withContext(Dispatchers.IO) {
        val reminderDao = db.reminderDao()
        val alive = mutableSetOf<Pair<String, Int>>()

        for (event in db.vehicleEventDao().getEventsWithNextDue()) {
            val dueAt = event.nextDueDate ?: continue
            alive += Reminder.SOURCE_EVENT to event.id
            upsert(
                dao = reminderDao,
                sourceType = Reminder.SOURCE_EVENT,
                sourceId = event.id,
                vehicleId = event.vehicleId,
                eventId = event.id,
                title = event.title,
                description = event.description,
                reminderType = event.eventType,
                dueAt = dueAt
            )
        }

        for (doc in db.vehicleDocumentDao().getDocumentsWithExpiry()) {
            val dueAt = doc.expiresAt ?: continue
            // Reminder.vehicleId is not nullable, so a personal document — one with no vehicle —
            // cannot yet generate one. Its expiry still shows on the document itself.
            val vehicleId = doc.vehicleId ?: continue
            alive += Reminder.SOURCE_DOCUMENT to doc.id
            upsert(
                dao = reminderDao,
                sourceType = Reminder.SOURCE_DOCUMENT,
                sourceId = doc.id,
                vehicleId = vehicleId,
                eventId = null,
                title = doc.title,
                description = doc.notes,
                reminderType = "document_expiry",
                dueAt = dueAt
            )
        }

        // Service schedules only become a date once there is something to project from: a time
        // interval since it was last done, or a distance interval plus a usage rate.
        val usageByVehicle = mutableMapOf<Int, OdometerSync.UsageStats>()
        for (schedule in db.serviceScheduleDao().getAllActive()) {
            val usage = usageByVehicle.getOrPut(schedule.vehicleId) {
                OdometerSync.statsFor(db, schedule.vehicleId)
            }
            val dueAt = schedule.projectedDueDate(usage) ?: continue
            alive += Reminder.SOURCE_SCHEDULE to schedule.id
            upsert(
                dao = reminderDao,
                sourceType = Reminder.SOURCE_SCHEDULE,
                sourceId = schedule.id,
                vehicleId = schedule.vehicleId,
                eventId = null,
                title = schedule.itemName,
                description = null,
                reminderType = "service",
                dueAt = dueAt
            )
        }

        // A source that lost its date, or was deleted outright, should take its reminder with it.
        // Manual reminders are never touched.
        for (orphan in reminderDao.getGenerated()) {
            val key = orphan.sourceType to (orphan.sourceId ?: -1)
            if (key !in alive) reminderDao.delete(orphan)
        }
    }

    private suspend fun upsert(
        dao: ReminderDao,
        sourceType: String,
        sourceId: Int,
        vehicleId: Int,
        eventId: Int?,
        title: String,
        description: String?,
        reminderType: String,
        dueAt: Long
    ) {
        val now = System.currentTimeMillis()
        val existing = dao.getBySource(sourceType, sourceId)

        when {
            existing == null -> dao.insert(
                Reminder(
                    vehicleId = vehicleId,
                    eventId = eventId,
                    title = title,
                    description = description,
                    reminderType = reminderType,
                    dueAt = dueAt,
                    sourceType = sourceType,
                    sourceId = sourceId,
                    createdAt = now,
                    updatedAt = now
                )
            )

            // The date moved, so this is a fresh obligation: clear any completion and any snooze,
            // otherwise next year's renewal stays silently ticked off from last year.
            existing.dueAt != dueAt -> dao.update(
                existing.copy(
                    title = title,
                    description = description,
                    reminderType = reminderType,
                    dueAt = dueAt,
                    isCompleted = false,
                    completedAt = null,
                    notifyAt = null,
                    updatedAt = now
                )
            )

            // Same date, so preserve completed and snoozed state and only refresh the wording.
            existing.title != title || existing.description != description -> dao.update(
                existing.copy(title = title, description = description, updatedAt = now)
            )
        }
    }
}
