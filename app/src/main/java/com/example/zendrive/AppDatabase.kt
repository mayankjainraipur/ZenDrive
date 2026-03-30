package com.example.zendrive

import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

// ─── Vehicle ────────────────────────────────────────────────────────────────

@Entity(tableName = "vehicle")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val vehicleNumber: String,
    /** e.g. "car", "bike", "auto", "truck" */
    val type: String,
    /** e.g. "petrol", "diesel", "electric", "hybrid" */
    val fuelType: String,
    val brand: String,
    val model: String,
    val year: Int,
    /** Stored as epoch-millis; null if not set */
    val purchaseDate: Long? = null,
    val odometerReading: Double = 0.0,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ─── VehicleEvent ────────────────────────────────────────────────────────────

@Entity(
    tableName = "vehicle_event",
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
data class VehicleEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleId: Int,
    /** e.g. "fuel", "service", "repair", "insurance", "tax", … */
    val eventType: String,
    val title: String,
    val description: String? = null,
    /** Event date stored as epoch-millis */
    val date: Long,
    val odometer: Double? = null,
    val cost: Double? = null,
    /** Next due date stored as epoch-millis; null if not applicable */
    val nextDueDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── EventMeta ───────────────────────────────────────────────────────────────

@Entity(
    tableName = "event_meta",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEvent::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("eventId")]
)
data class EventMeta(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val eventId: Int,
    val key: String,
    val value: String
)

// ─── AppDatabase ─────────────────────────────────────────────────────────────

@Database(
    entities = [Vehicle::class, VehicleEvent::class, EventMeta::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun vehicleEventDao(): VehicleEventDao
    abstract fun eventMetaDao(): EventMetaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zendrive_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
