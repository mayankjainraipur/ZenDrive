package io.github.mayankjainraipur.zendrive

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun observeProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile)
}

@Dao
interface VehicleDocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: VehicleDocument): Long

    @Update
    suspend fun update(document: VehicleDocument)

    @Delete
    suspend fun delete(document: VehicleDocument)

    @Query("SELECT * FROM vehicle_documents WHERE vehicleId = :vehicleId ORDER BY createdAt DESC")
    suspend fun getDocumentsForVehicle(vehicleId: Int): List<VehicleDocument>

    @Query("SELECT * FROM vehicle_documents WHERE id = :id")
    suspend fun getById(id: Int): VehicleDocument?

    @Query("SELECT localFileName FROM vehicle_documents WHERE localFileName IS NOT NULL")
    suspend fun getAllLocalFileNames(): List<String>

    @Query("SELECT * FROM vehicle_documents WHERE localFileName IS NULL")
    suspend fun getDocumentsMissingLocalCopy(): List<VehicleDocument>

    @Query("SELECT * FROM vehicle_documents WHERE expiresAt IS NOT NULL")
    suspend fun getDocumentsWithExpiry(): List<VehicleDocument>

    @Query("DELETE FROM vehicle_documents WHERE vehicleId = :vehicleId")
    suspend fun deleteAllForVehicle(vehicleId: Int)
}

@Dao
interface ReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("SELECT * FROM reminder WHERE vehicleId = :vehicleId ORDER BY dueAt ASC")
    suspend fun getRemindersForVehicle(vehicleId: Int): List<Reminder>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun getById(id: Int): Reminder?

    /**
     * What should be announced right now: due within the lead window, not already done, and not
     * snoozed past [nowMillis]. notifyAt is the snooze override — it suppresses the notification
     * without moving the actual due date.
     */
    @Query(
        """
        SELECT * FROM reminder
        WHERE isCompleted = 0
          AND dueAt <= :untilMillis
          AND (notifyAt IS NULL OR notifyAt <= :nowMillis)
        ORDER BY dueAt ASC
        """
    )
    suspend fun getDueOrOverdue(untilMillis: Long, nowMillis: Long): List<Reminder>

    @Query("SELECT * FROM reminder WHERE sourceType = :sourceType AND sourceId = :sourceId LIMIT 1")
    suspend fun getBySource(sourceType: String, sourceId: Int): Reminder?

    /** Every auto-generated reminder, for reconciling against its source. */
    @Query("SELECT * FROM reminder WHERE sourceType != 'manual'")
    suspend fun getGenerated(): List<Reminder>

    /** Across all vehicles, soonest first — what the dashboard leads with. */
    @Query("SELECT * FROM reminder WHERE isCompleted = 0 ORDER BY dueAt ASC LIMIT :limit")
    suspend fun getUpcoming(limit: Int): List<Reminder>

    @Query("DELETE FROM reminder WHERE vehicleId = :vehicleId")
    suspend fun deleteAllForVehicle(vehicleId: Int)
}

@Dao
interface OdometerLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: OdometerLog): Long

    @Update
    suspend fun update(entry: OdometerLog)

    @Delete
    suspend fun delete(entry: OdometerLog)

    /** Newest first, for display. */
    @Query("SELECT * FROM odometer_log WHERE vehicleId = :vehicleId ORDER BY recordedAt DESC")
    suspend fun getForVehicle(vehicleId: Int): List<OdometerLog>

    /** Oldest first, for rate calculations that walk forward through time. */
    @Query("SELECT * FROM odometer_log WHERE vehicleId = :vehicleId ORDER BY recordedAt ASC")
    suspend fun getForVehicleAsc(vehicleId: Int): List<OdometerLog>

    @Query("SELECT * FROM odometer_log WHERE source = 'event' AND eventId = :eventId LIMIT 1")
    suspend fun getByEvent(eventId: Int): OdometerLog?

    @Query("SELECT * FROM odometer_log WHERE source = 'event'")
    suspend fun getDerived(): List<OdometerLog>

    @Query("SELECT * FROM odometer_log WHERE source = 'manual'")
    suspend fun getManual(): List<OdometerLog>

    @Query("DELETE FROM odometer_log WHERE vehicleId = :vehicleId")
    suspend fun deleteAllForVehicle(vehicleId: Int)
}

@Dao
interface AttachmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: Attachment): Long

    @Delete
    suspend fun delete(attachment: Attachment)

    @Query("SELECT * FROM attachment WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt ASC")
    suspend fun getForOwner(ownerType: String, ownerId: Int): List<Attachment>

    @Query("SELECT * FROM attachment")
    suspend fun getAll(): List<Attachment>

    @Query("SELECT localFileName FROM attachment")
    suspend fun getAllLocalFileNames(): List<String>

    @Query("DELETE FROM attachment WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteForOwner(ownerType: String, ownerId: Int)
}

@Dao
interface BackupRestoreLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BackupRestoreLog): Long

    @Query("SELECT * FROM backup_restore_log ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<BackupRestoreLog>

    @Query("SELECT * FROM backup_restore_log WHERE id = :id")
    suspend fun getById(id: Int): BackupRestoreLog?
}