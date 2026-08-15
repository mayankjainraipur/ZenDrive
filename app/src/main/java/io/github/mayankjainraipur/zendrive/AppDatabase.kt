package io.github.mayankjainraipur.zendrive

import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
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
    /** Litres (or gallons) added. Fuel events only; null everywhere else. */
    val fuelVolume: Double? = null,
    /** Price per litre/gallon at the time of the fill. */
    val pricePerUnit: Double? = null,
    /**
     * Whether the tank was filled to the brim. Mileage is measured between two full tanks, so
     * a partial fill contributes its volume to the next full-to-full stretch but cannot end one.
     */
    val isFullTank: Boolean = false,
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
    entities = [
        Vehicle::class,
        VehicleEvent::class,
        EventMeta::class,
        UserProfile::class,
        VehicleDocument::class,
        Reminder::class,
        BackupRestoreLog::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun vehicleEventDao(): VehicleEventDao
    abstract fun eventMetaDao(): EventMetaDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun vehicleDocumentDao(): VehicleDocumentDao
    abstract fun reminderDao(): ReminderDao
    abstract fun backupRestoreLogDao(): BackupRestoreLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_profile` (
                      `id` INTEGER NOT NULL,
                      `displayName` TEXT NOT NULL,
                      `email` TEXT NOT NULL,
                      `mobileNumber` TEXT,
                      `preferredCurrencyCode` TEXT NOT NULL,
                      `backupEnabled` INTEGER NOT NULL,
                      `lastBackupAt` INTEGER,
                      `lastRestoreAt` INTEGER,
                      `driveAccountEmail` TEXT,
                      `createdAt` INTEGER NOT NULL,
                      `updatedAt` INTEGER NOT NULL,
                      PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vehicle_documents` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `vehicleId` INTEGER NOT NULL,
                      `title` TEXT NOT NULL,
                      `documentType` TEXT NOT NULL,
                      `fileName` TEXT NOT NULL,
                      `mimeType` TEXT,
                      `storageUri` TEXT NOT NULL,
                      `fileSizeBytes` INTEGER,
                      `expiresAt` INTEGER,
                      `notes` TEXT,
                      `createdAt` INTEGER NOT NULL,
                      `updatedAt` INTEGER NOT NULL,
                      FOREIGN KEY(`vehicleId`) REFERENCES `vehicle`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vehicle_documents_vehicleId` " +
                        "ON `vehicle_documents` (`vehicleId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vehicle_documents_vehicleId_documentType` " +
                        "ON `vehicle_documents` (`vehicleId`, `documentType`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminder` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `vehicleId` INTEGER NOT NULL,
                      `eventId` INTEGER,
                      `title` TEXT NOT NULL,
                      `description` TEXT,
                      `reminderType` TEXT NOT NULL,
                      `dueAt` INTEGER NOT NULL,
                      `repeatRule` TEXT NOT NULL,
                      `isCompleted` INTEGER NOT NULL,
                      `completedAt` INTEGER,
                      `notifyAt` INTEGER,
                      `createdAt` INTEGER NOT NULL,
                      `updatedAt` INTEGER NOT NULL,
                      FOREIGN KEY(`vehicleId`) REFERENCES `vehicle`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                      FOREIGN KEY(`eventId`) REFERENCES `vehicle_event`(`id`)
                        ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminder_vehicleId` ON `reminder` (`vehicleId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminder_eventId` ON `reminder` (`eventId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminder_dueAt` ON `reminder` (`dueAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminder_vehicleId_dueAt` " +
                        "ON `reminder` (`vehicleId`, `dueAt`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `backup_restore_log` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `operationType` TEXT NOT NULL,
                      `startedAt` INTEGER NOT NULL,
                      `completedAt` INTEGER,
                      `status` TEXT NOT NULL,
                      `driveFileId` TEXT,
                      `bytesProcessed` INTEGER,
                      `errorMessage` TEXT,
                      `clientAppVersion` TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `distanceUnit` TEXT NOT NULL DEFAULT 'km'")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `dateFormatPattern` TEXT NOT NULL DEFAULT 'dd MMM yyyy'")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `themeMode` TEXT NOT NULL DEFAULT 'dark'")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `reminderLeadDays` INTEGER NOT NULL DEFAULT 3")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `vehicle` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `vehicle` ADD COLUMN `archivedAt` INTEGER")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `appLockEnabled` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `vehicle_documents` ADD COLUMN `localFileName` TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Everything that exists already was created by hand, so it is manual by definition.
                db.execSQL(
                    "ALTER TABLE `reminder` ADD COLUMN `sourceType` TEXT NOT NULL DEFAULT 'manual'"
                )
                db.execSQL("ALTER TABLE `reminder` ADD COLUMN `sourceId` INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminder_sourceType_sourceId` " +
                        "ON `reminder` (`sourceType`, `sourceId`)"
                )
                // Adopt the reminders the old AddEventActivity auto-created, so the reconciler
                // takes them over instead of adding a second one beside each. Those rows are
                // identifiable by the "<title> — due" wording it used; a reminder the user wrote
                // themselves and merely linked to an event stays manual.
                db.execSQL(
                    """
                    UPDATE `reminder`
                    SET `sourceType` = 'event', `sourceId` = `eventId`
                    WHERE `eventId` IS NOT NULL AND `title` LIKE '%' || ' — due'
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `vehicle_event` ADD COLUMN `fuelVolume` REAL")
                db.execSQL("ALTER TABLE `vehicle_event` ADD COLUMN `pricePerUnit` REAL")
                // Existing fuel records have no volume, so they cannot contribute to mileage
                // either way; false is the honest default rather than a guess about past fills.
                db.execSQL(
                    "ALTER TABLE `vehicle_event` ADD COLUMN `isFullTank` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** Single source of truth for the migration chain — the builder and the tests share it. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7
        )

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zendrive_db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}