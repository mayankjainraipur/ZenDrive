package com.example.zendrive

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

    @Query(
        "SELECT * FROM reminder WHERE isCompleted = 0 AND dueAt <= :untilMillis ORDER BY dueAt ASC"
    )
    suspend fun getDueOrOverdue(untilMillis: Long): List<Reminder>

    @Query("DELETE FROM reminder WHERE vehicleId = :vehicleId")
    suspend fun deleteAllForVehicle(vehicleId: Int)
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
