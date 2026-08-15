package io.github.mayankjainraipur.zendrive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Derives odometer log entries from the readings already recorded on events, and works out how
 * fast a vehicle is being used.
 *
 * A single latest reading says where the vehicle is; a series says how quickly it got there,
 * which is what lets a distance-based service interval be expressed as a date.
 */
object OdometerSync {

    data class UsageStats(
        val latestReading: Double?,
        val latestAt: Long?,
        /** Distance covered per day, averaged across the whole log. Null until measurable. */
        val perDay: Double?,
        val entryCount: Int
    ) {
        val perMonth: Double? get() = perDay?.let { it * 30 }

        /** Projected odometer at [atMillis], or null when there is no rate to project with. */
        fun projectedAt(atMillis: Long): Double? {
            val reading = latestReading ?: return null
            val from = latestAt ?: return null
            val rate = perDay ?: return null
            val days = TimeUnit.MILLISECONDS.toDays(atMillis - from)
            if (days <= 0) return reading
            return reading + rate * days
        }

        /** When the odometer is expected to reach [target], or null if it already has. */
        fun estimatedDateFor(target: Double): Long? {
            val reading = latestReading ?: return null
            val from = latestAt ?: return null
            val rate = perDay?.takeIf { it > 0 } ?: return null
            if (target <= reading) return null
            val daysNeeded = (target - reading) / rate
            return from + TimeUnit.DAYS.toMillis(daysNeeded.toLong())
        }
    }

    /** Keeps event-derived readings in step; readings entered by hand are never touched. */
    suspend fun reconcile(db: AppDatabase) = withContext(Dispatchers.IO) {
        val logDao = db.odometerLogDao()
        val alive = mutableSetOf<Int>()

        for (event in db.vehicleEventDao().getAllEventsWithOdometer()) {
            val reading = event.odometer ?: continue
            if (reading <= 0) continue
            alive += event.id

            val existing = logDao.getByEvent(event.id)
            if (existing == null) {
                logDao.insert(
                    OdometerLog(
                        vehicleId = event.vehicleId,
                        reading = reading,
                        recordedAt = event.date,
                        source = OdometerLog.SOURCE_EVENT,
                        eventId = event.id
                    )
                )
            } else if (existing.reading != reading || existing.recordedAt != event.date) {
                logDao.update(existing.copy(reading = reading, recordedAt = event.date))
            }
        }

        for (derived in logDao.getDerived()) {
            if (derived.eventId !in alive) logDao.delete(derived)
        }
    }

    suspend fun statsFor(db: AppDatabase, vehicleId: Int): UsageStats =
        withContext(Dispatchers.IO) {
            val entries = db.odometerLogDao().getForVehicleAsc(vehicleId)
            if (entries.isEmpty()) return@withContext UsageStats(null, null, null, 0)

            val newest = entries.last()
            val oldest = entries.first()

            // Measured across the whole span rather than the last few readings: a short recent
            // gap between two fills on the same weekend would otherwise imply an absurd rate.
            val elapsedDays = TimeUnit.MILLISECONDS.toDays(newest.recordedAt - oldest.recordedAt)
            val distance = newest.reading - oldest.reading
            val perDay = if (elapsedDays >= 1 && distance > 0) distance / elapsedDays else null

            UsageStats(
                latestReading = newest.reading,
                latestAt = newest.recordedAt,
                perDay = perDay,
                entryCount = entries.size
            )
        }

    /** Records a reading entered by hand and moves the vehicle's headline number forward. */
    suspend fun recordManualReading(
        db: AppDatabase,
        vehicleId: Int,
        reading: Double,
        recordedAt: Long = System.currentTimeMillis(),
        note: String? = null
    ) = withContext(Dispatchers.IO) {
        db.odometerLogDao().insert(
            OdometerLog(
                vehicleId = vehicleId,
                reading = reading,
                recordedAt = recordedAt,
                source = OdometerLog.SOURCE_MANUAL,
                note = note
            )
        )
        val vehicle = db.vehicleDao().getVehicleById(vehicleId) ?: return@withContext
        if (reading > vehicle.odometerReading) {
            db.vehicleDao().updateVehicle(
                vehicle.copy(odometerReading = reading, updatedAt = System.currentTimeMillis())
            )
        }
    }
}
