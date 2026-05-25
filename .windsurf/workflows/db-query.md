---
description: Write Room database query pattern
---

Add a database query to ZenDrive following project patterns.

**In DAO interface:**

1. Simple query returning Flow (for UI observation):
   ```kotlin
   @Query("SELECT * FROM vehicle WHERE id = :vehicleId")
   fun getVehicle(vehicleId: Long): Flow<Vehicle>
   ```

2. One-time query (suspend):
   ```kotlin
   @Query("SELECT * FROM vehicle_event WHERE vehicle_id = :vehicleId ORDER BY date DESC")
   suspend fun getEventsForVehicle(vehicleId: Long): List<VehicleEvent>
   ```

3. Join query:
   ```kotlin
   @Query("""
       SELECT v.*, COUNT(e.id) as event_count 
       FROM vehicle v 
       LEFT JOIN vehicle_event e ON v.id = e.vehicle_id 
       GROUP BY v.id
   """)
   fun getVehiclesWithEventCount(): Flow<List<VehicleWithCount>>
   ```

**In ViewModel:**
```kotlin
val vehicles: StateFlow<List<Vehicle>> = vehicleDao.getAllVehicles()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

**In Activity (collect):**
```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.vehicles.collect { list ->
            adapter.submitList(list)
        }
    }
}
```

**Transaction for multi-table:**
```kotlin
@Transaction
@Query("SELECT * FROM vehicle WHERE id = :id")
fun getVehicleWithEvents(id: Long): Flow<VehicleWithEvents>
```
