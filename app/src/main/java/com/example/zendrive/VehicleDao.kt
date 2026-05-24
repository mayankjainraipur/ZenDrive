package com.example.zendrive

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ─── VehicleDao ──────────────────────────────────────────────────────────────

@Dao
interface VehicleDao {

    // --- Insert / Update / Delete ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle): Long

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Delete
    suspend fun deleteVehicle(vehicle: Vehicle)

    // --- Queries ---

    @Query("SELECT * FROM vehicle ORDER BY createdAt DESC")
    suspend fun getAllVehicles(): List<Vehicle>

    @Query("SELECT * FROM vehicle ORDER BY createdAt DESC")
    fun observeAllVehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicle WHERE id = :vehicleId")
    suspend fun getVehicleById(vehicleId: Int): Vehicle?

    @Query("SELECT * FROM vehicle WHERE type = :type ORDER BY createdAt DESC")
    suspend fun getVehiclesByType(type: String): List<Vehicle>

    @Query("SELECT * FROM vehicle WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun observeActiveVehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicle WHERE isArchived = 1 ORDER BY archivedAt DESC")
    fun observeArchivedVehicles(): Flow<List<Vehicle>>
}

// ─── CategoryExpense (POJO for grouped query results) ───────────────────────

data class CategoryExpense(
    val eventType: String,
    val totalCost: Double
)

// ─── VehicleEventDao ─────────────────────────────────────────────────────────

@Dao
interface VehicleEventDao {

    // --- Insert / Update / Delete ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: VehicleEvent): Long

    @Update
    suspend fun updateEvent(event: VehicleEvent)

    @Delete
    suspend fun deleteEvent(event: VehicleEvent)

    // --- Queries ---

    @Query("SELECT * FROM vehicle_event WHERE vehicleId = :vehicleId ORDER BY date DESC")
    suspend fun getEventsForVehicle(vehicleId: Int): List<VehicleEvent>

    @Query("SELECT * FROM vehicle_event WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun observeEventsForVehicle(vehicleId: Int): Flow<List<VehicleEvent>>

    @Query("SELECT * FROM vehicle_event WHERE vehicleId = :vehicleId AND eventType = :eventType ORDER BY date DESC")
    suspend fun getEventsByType(vehicleId: Int, eventType: String): List<VehicleEvent>

    @Query("SELECT * FROM vehicle_event WHERE id = :eventId")
    suspend fun getEventById(eventId: Int): VehicleEvent?

    @Query("SELECT * FROM vehicle_event WHERE nextDueDate IS NOT NULL AND nextDueDate <= :epochMillis ORDER BY nextDueDate ASC")
    suspend fun getUpcomingEvents(epochMillis: Long): List<VehicleEvent>

    @Query("DELETE FROM vehicle_event WHERE vehicleId = :vehicleId")
    suspend fun deleteAllEventsForVehicle(vehicleId: Int)

    @Query(
        """
        SELECT * FROM vehicle_event 
        WHERE vehicleId = :vehicleId 
        AND cost IS NOT NULL 
        AND cost > 0 
        AND date >= :startDate 
        AND date <= :endDate 
        ORDER BY date DESC
        """
    )
    suspend fun getExpensesForVehicleInRange(
        vehicleId: Int,
        startDate: Long,
        endDate: Long
    ): List<VehicleEvent>

    @Query(
        """
        SELECT SUM(cost) FROM vehicle_event 
        WHERE vehicleId = :vehicleId 
        AND cost IS NOT NULL 
        AND cost > 0 
        AND date >= :startDate 
        AND date <= :endDate
        """
    )
    suspend fun getTotalExpensesForVehicleInRange(
        vehicleId: Int,
        startDate: Long,
        endDate: Long
    ): Double?

    @Query("SELECT * FROM vehicle_event WHERE vehicleId = :vehicleId AND eventType = 'fuel' ORDER BY date ASC")
    suspend fun getFuelEventsAsc(vehicleId: Int): List<VehicleEvent>

    @Query(
        """
        SELECT eventType, SUM(cost) as totalCost FROM vehicle_event 
        WHERE vehicleId = :vehicleId 
        AND cost > 0 
        AND date >= :startDate 
        AND date <= :endDate 
        GROUP BY eventType
        """
    )
    suspend fun getExpensesByCategory(
        vehicleId: Int,
        startDate: Long,
        endDate: Long
    ): List<CategoryExpense>
}

// ─── EventMetaDao ────────────────────────────────────────────────────────────

@Dao
interface EventMetaDao {

    // --- Insert / Update / Delete ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeta(meta: EventMeta): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMeta(metaList: List<EventMeta>)

    @Update
    suspend fun updateMeta(meta: EventMeta)

    @Delete
    suspend fun deleteMeta(meta: EventMeta)

    // --- Queries ---

    @Query("SELECT * FROM event_meta WHERE eventId = :eventId")
    suspend fun getMetaForEvent(eventId: Int): List<EventMeta>

    @Query("SELECT * FROM event_meta WHERE eventId = :eventId AND `key` = :key LIMIT 1")
    suspend fun getMetaValue(eventId: Int, key: String): EventMeta?

    @Query("DELETE FROM event_meta WHERE eventId = :eventId")
    suspend fun deleteAllMetaForEvent(eventId: Int)
}
