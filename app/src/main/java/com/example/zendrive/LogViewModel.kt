package com.example.zendrive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LogViewModel(
    private val vehicleDao: VehicleDao,
    private val eventDao: VehicleEventDao,
    private val metaDao: EventMetaDao,
    private val userProfileDao: UserProfileDao
) : ViewModel() {

    val userProfileFlow: Flow<UserProfile?> = userProfileDao.observeProfile()

    fun saveUserProfile(
        displayName: String,
        email: String,
        mobile: String?,
        currencyCode: String,
        existing: UserProfile?
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val trimmedCurrency = currencyCode.trim().ifBlank { "INR" }
            val profile = UserProfile(
                id = 1,
                displayName = displayName.trim(),
                email = email.trim(),
                mobileNumber = mobile?.trim()?.takeIf { it.isNotBlank() },
                preferredCurrencyCode = trimmedCurrency,
                backupEnabled = existing?.backupEnabled ?: false,
                lastBackupAt = existing?.lastBackupAt,
                lastRestoreAt = existing?.lastRestoreAt,
                driveAccountEmail = existing?.driveAccountEmail,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            userProfileDao.upsert(profile)
        }
    }

    // ─── Vehicle state ────────────────────────────────────────────────────────

    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles

    fun fetchVehicles() {
        viewModelScope.launch {
            _vehicles.value = vehicleDao.getAllVehicles()
        }
    }

    fun addVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleDao.insertVehicle(vehicle)
            fetchVehicles()
        }
    }

    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleDao.updateVehicle(vehicle)
            fetchVehicles()
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleDao.deleteVehicle(vehicle)
            fetchVehicles()
        }
    }

    // ─── VehicleEvent state ───────────────────────────────────────────────────

    private val _events = MutableStateFlow<List<VehicleEvent>>(emptyList())
    val events: StateFlow<List<VehicleEvent>> = _events

    fun fetchEventsForVehicle(vehicleId: Int) {
        viewModelScope.launch {
            _events.value = eventDao.getEventsForVehicle(vehicleId)
        }
    }

    fun addEvent(event: VehicleEvent) {
        viewModelScope.launch {
            eventDao.insertEvent(event)
            fetchEventsForVehicle(event.vehicleId)
        }
    }

    fun updateEvent(event: VehicleEvent) {
        viewModelScope.launch {
            eventDao.updateEvent(event)
            fetchEventsForVehicle(event.vehicleId)
        }
    }

    fun deleteEvent(event: VehicleEvent) {
        viewModelScope.launch {
            eventDao.deleteEvent(event)
            fetchEventsForVehicle(event.vehicleId)
        }
    }

    // ─── EventMeta state ──────────────────────────────────────────────────────

    private val _meta = MutableStateFlow<List<EventMeta>>(emptyList())
    val meta: StateFlow<List<EventMeta>> = _meta

    fun fetchMetaForEvent(eventId: Int) {
        viewModelScope.launch {
            _meta.value = metaDao.getMetaForEvent(eventId)
        }
    }

    fun addMeta(meta: EventMeta) {
        viewModelScope.launch {
            metaDao.insertMeta(meta)
            fetchMetaForEvent(meta.eventId)
        }
    }

    fun deleteMeta(meta: EventMeta) {
        viewModelScope.launch {
            metaDao.deleteMeta(meta)
            fetchMetaForEvent(meta.eventId)
        }
    }
}
