package io.github.mayankjainraipur.zendrive

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A dated odometer reading, independent of any event.
 *
 * The vehicle row only ever held the latest number, which says where the vehicle is but not how
 * fast it got there. A series of readings gives a usage rate, and a usage rate is what turns
 * "service every 5,000 km" into a date that can be reminded against.
 *
 * Readings taken from events are derived rather than owned: [OdometerSync] keeps them in step,
 * the same way [ReminderSync] does for generated reminders.
 */
@Entity(
    tableName = "odometer_log",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("vehicleId"),
        Index(value = ["vehicleId", "recordedAt"]),
        Index(value = ["source", "eventId"])
    ]
)
data class OdometerLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleId: Int,
    val reading: Double,
    /** Epoch millis the reading was taken — not when the row was written. */
    val recordedAt: Long,
    /** [SOURCE_MANUAL] or [SOURCE_EVENT]. */
    val source: String = SOURCE_MANUAL,
    /** The event this reading came from, when derived. Null for a reading entered by hand. */
    val eventId: Int? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isDerived: Boolean get() = source == SOURCE_EVENT

    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_EVENT = "event"
    }
}
