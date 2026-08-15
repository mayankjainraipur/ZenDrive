package io.github.mayankjainraipur.zendrive

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * A recurring maintenance item — oil, brake pads, chain — due on whichever of distance or time
 * arrives first.
 *
 * Both intervals are optional but at least one is meaningful: a yearly inspection has only months,
 * a chain clean only kilometres, an oil change both.
 */
@Entity(
    tableName = "service_schedule",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vehicleId")]
)
data class ServiceSchedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleId: Int,
    val itemName: String,
    /** Distance between services; null when the item is purely time-based. */
    val intervalKm: Double? = null,
    /** Months between services; null when the item is purely distance-based. */
    val intervalMonths: Int? = null,
    /** When it was last done. Null means never — due from the vehicle's current state. */
    val lastDoneAt: Long? = null,
    val lastDoneOdometer: Double? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {

    /** The odometer figure this item next falls due at, if it has a distance interval. */
    fun dueAtOdometer(): Double? {
        val interval = intervalKm ?: return null
        val from = lastDoneOdometer ?: return null
        return from + interval
    }

    /** The date this item next falls due by time alone, if it has a month interval. */
    fun dueAtDate(): Long? {
        val months = intervalMonths ?: return null
        val from = lastDoneAt ?: return null
        return Calendar.getInstance().apply {
            timeInMillis = from
            add(Calendar.MONTH, months)
        }.timeInMillis
    }

    /**
     * The date this actually falls due, taking whichever limit arrives first.
     *
     * A distance limit only becomes a date once there is a usage rate to project with — without
     * one, "in 2,000 km" cannot be placed on a calendar, so the time limit stands alone.
     */
    fun projectedDueDate(usage: OdometerSync.UsageStats): Long? {
        val byDate = dueAtDate()
        val byOdometer = dueAtOdometer()?.let { usage.estimatedDateFor(it) }
        return when {
            byDate != null && byOdometer != null -> minOf(byDate, byOdometer)
            else -> byDate ?: byOdometer
        }
    }

    /** Distance still to run before the distance limit, or null when there isn't one. */
    fun remainingDistance(currentOdometer: Double?): Double? {
        val target = dueAtOdometer() ?: return null
        val current = currentOdometer ?: return null
        return target - current
    }

    companion object {

        /** A starting set per vehicle type, so a new schedule is not a blank page. */
        fun templatesFor(vehicleType: String): List<ServiceSchedule> = when (vehicleType.lowercase()) {
            "bike" -> listOf(
                template("Engine oil", 3000.0, 4),
                template("Chain clean & lube", 500.0, 1),
                template("Brake pads", 10000.0, 18),
                template("Air filter", 8000.0, 12),
                template("General service", 5000.0, 6)
            )
            "truck", "bus" -> listOf(
                template("Engine oil", 10000.0, 6),
                template("Air filter", 20000.0, 12),
                template("Brake inspection", 15000.0, 6),
                template("Tyre rotation", 20000.0, 12),
                template("Fitness check", null, 12)
            )
            "auto" -> listOf(
                template("Engine oil", 4000.0, 4),
                template("Brake pads", 12000.0, 18),
                template("General service", 5000.0, 6)
            )
            // Cars, and anything unrecognised.
            else -> listOf(
                template("Engine oil & filter", 5000.0, 6),
                template("Air filter", 10000.0, 12),
                template("Brake pads", 20000.0, 24),
                template("Tyre rotation", 10000.0, 6),
                template("Coolant", 40000.0, 24),
                template("General service", 10000.0, 12)
            )
        }

        private fun template(name: String, km: Double?, months: Int?) =
            ServiceSchedule(vehicleId = 0, itemName = name, intervalKm = km, intervalMonths = months)

        /** Days from now until [dueAt], in whole calendar days. */
        fun daysUntil(dueAt: Long): Int {
            val startOfToday = startOfDay(System.currentTimeMillis())
            return TimeUnit.MILLISECONDS.toDays(startOfDay(dueAt) - startOfToday).toInt()
        }

        private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
