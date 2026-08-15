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
    /** Manufacturer warranty expiry, epoch millis; null when unknown or lapsed long ago. */
    val warrantyExpiresAt: Long? = null,
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
        OdometerLog::class,
        Attachment::class,
        ServiceSchedule::class,
        BackupRestoreLog::class
    ],
    version = 11,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun vehicleEventDao(): VehicleEventDao
    abstract fun eventMetaDao(): EventMetaDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun vehicleDocumentDao(): VehicleDocumentDao
    abstract fun reminderDao(): ReminderDao
    abstract fun odometerLogDao(): OdometerLogDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun serviceScheduleDao(): ServiceScheduleDao
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

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `odometer_log` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `vehicleId` INTEGER NOT NULL,
                      `reading` REAL NOT NULL,
                      `recordedAt` INTEGER NOT NULL,
                      `source` TEXT NOT NULL,
                      `eventId` INTEGER,
                      `note` TEXT,
                      `createdAt` INTEGER NOT NULL,
                      FOREIGN KEY(`vehicleId`) REFERENCES `vehicle`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_odometer_log_vehicleId` " +
                        "ON `odometer_log` (`vehicleId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_odometer_log_vehicleId_recordedAt` " +
                        "ON `odometer_log` (`vehicleId`, `recordedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_odometer_log_source_eventId` " +
                        "ON `odometer_log` (`source`, `eventId`)"
                )
                // Events have been carrying odometer readings all along. Seeding from them means
                // a usage rate exists immediately instead of starting from nothing.
                db.execSQL(
                    """
                    INSERT INTO `odometer_log`
                      (`vehicleId`, `reading`, `recordedAt`, `source`, `eventId`, `createdAt`)
                    SELECT `vehicleId`, `odometer`, `date`, 'event', `id`, `createdAt`
                    FROM `vehicle_event`
                    WHERE `odometer` IS NOT NULL AND `odometer` > 0
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No foreign key: the owner is polymorphic, so AttachmentStore.pruneOrphans does
                // the cleanup a cascade would otherwise handle.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `attachment` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `ownerType` TEXT NOT NULL,
                      `ownerId` INTEGER NOT NULL,
                      `localFileName` TEXT NOT NULL,
                      `mimeType` TEXT,
                      `caption` TEXT,
                      `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_attachment_ownerType_ownerId` " +
                        "ON `attachment` (`ownerType`, `ownerId`)"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `service_schedule` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `vehicleId` INTEGER NOT NULL,
                      `itemName` TEXT NOT NULL,
                      `intervalKm` REAL,
                      `intervalMonths` INTEGER,
                      `lastDoneAt` INTEGER,
                      `lastDoneOdometer` REAL,
                      `isActive` INTEGER NOT NULL DEFAULT 1,
                      `createdAt` INTEGER NOT NULL,
                      `updatedAt` INTEGER NOT NULL,
                      FOREIGN KEY(`vehicleId`) REFERENCES `vehicle`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_service_schedule_vehicleId` " +
                        "ON `service_schedule` (`vehicleId`)"
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `vehicle` ADD COLUMN `warrantyExpiresAt` INTEGER")

                // Personal documents -- a driving licence, say -- belong to the owner, not to a
                // vehicle. SQLite cannot drop a NOT NULL constraint in place, so the table is
                // rebuilt with vehicleId nullable and the rows copied across.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vehicle_documents_new` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `vehicleId` INTEGER,
                      `title` TEXT NOT NULL,
                      `documentType` TEXT NOT NULL,
                      `fileName` TEXT NOT NULL,
                      `mimeType` TEXT,
                      `storageUri` TEXT NOT NULL,
                      `localFileName` TEXT,
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
                    """
                    INSERT INTO `vehicle_documents_new`
                      (`id`, `vehicleId`, `title`, `documentType`, `fileName`, `mimeType`,
                       `storageUri`, `localFileName`, `fileSizeBytes`, `expiresAt`, `notes`,
                       `createdAt`, `updatedAt`)
                    SELECT `id`, `vehicleId`, `title`, `documentType`, `fileName`, `mimeType`,
                           `storageUri`, `localFileName`, `fileSizeBytes`, `expiresAt`, `notes`,
                           `createdAt`, `updatedAt`
                    FROM `vehicle_documents`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `vehicle_documents`")
                db.execSQL("ALTER TABLE `vehicle_documents_new` RENAME TO `vehicle_documents`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vehicle_documents_vehicleId` " +
                        "ON `vehicle_documents` (`vehicleId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vehicle_documents_vehicleId_documentType` " +
                        "ON `vehicle_documents` (`vehicleId`, `documentType`)"
                )
            }
        }

        /** Single source of truth for the migration chain — the builder and the tests share it. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11
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