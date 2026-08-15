package io.github.mayankjainraipur.zendrive

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Scheduled reminder independent of [VehicleEvent], for richer notification / recurrence UX later.
 */
@Entity(
    tableName = "reminder",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VehicleEvent::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("vehicleId"),
        Index("eventId"),
        Index(value = ["dueAt"]),
        Index(value = ["vehicleId", "dueAt"]),
        Index(value = ["sourceType", "sourceId"])
    ]
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleId: Int,
    /** Optional link to an existing maintenance/event row */
    val eventId: Int? = null,
    val title: String,
    val description: String? = null,
    /** e.g. service, insurance, tax, document_expiry, custom */
    val reminderType: String,
    /** When the reminder is due, epoch millis */
    val dueAt: Long,
    /** e.g. none, daily, weekly, monthly, yearly */
    val repeatRule: String = "none",
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    /**
     * Hold off notifying until this instant, even though [dueAt] has arrived — this is what
     * Snooze sets. Null means notify on the normal schedule. Distinct from [dueAt] on purpose:
     * snoozing changes when you are told, not when the thing is actually due.
     */
    val notifyAt: Long? = null,
    /** [SOURCE_MANUAL], [SOURCE_EVENT] or [SOURCE_DOCUMENT]. */
    val sourceType: String = SOURCE_MANUAL,
    /** Id of the event or document this was generated from; null for manual reminders. */
    val sourceId: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isGenerated: Boolean get() = sourceType != SOURCE_MANUAL

    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_EVENT = "event"
        const val SOURCE_DOCUMENT = "document"
    }
}