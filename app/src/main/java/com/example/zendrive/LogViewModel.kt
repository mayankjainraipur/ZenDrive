package com.example.zendrive

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModel(
    private val vehicleDao: VehicleDao,
    private val eventDao: VehicleEventDao,
    private val metaDao: EventMetaDao,
    private val userProfileDao: UserProfileDao,
    private val db: AppDatabase? = null
) : ViewModel() {

    val userProfileFlow: Flow<UserProfile?> = userProfileDao.observeProfile()

    // ─── Drive connection ─────────────────────────────────────────────────────

    private val _driveAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val driveAccount: StateFlow<GoogleSignInAccount?> = _driveAccount

    private val _driveBackups = MutableStateFlow<List<DriveBackupInfo>>(emptyList())
    val driveBackups: StateFlow<List<DriveBackupInfo>> = _driveBackups

    // ─── Sync status ─────────────────────────────────────────────────────────

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object InProgress : SyncStatus()
        data class Success(val message: String) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    // ─── Backup / Restore status ─────────────────────────────────────────────

    private val _backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus

    sealed class BackupStatus {
        object Idle : BackupStatus()
        object InProgress : BackupStatus()
        data class Success(val message: String) : BackupStatus()
        data class Error(val message: String) : BackupStatus()
    }

    private val _backupHistory = MutableStateFlow<List<BackupRestoreLog>>(emptyList())
    val backupHistory: StateFlow<List<BackupRestoreLog>> = _backupHistory

    fun loadBackupHistory() {
        val database = db ?: return
        viewModelScope.launch {
            _backupHistory.value = database.backupRestoreLogDao().getRecent(5)
        }
    }

    fun exportBackup(context: Context, uri: Uri) {
        val database = db ?: run {
            _backupStatus.value = BackupStatus.Error("Database not available")
            return
        }
        _backupStatus.value = BackupStatus.InProgress
        viewModelScope.launch {
            try {
                JsonBackupManager.exportToUri(database, context, uri)
                _backupStatus.value = BackupStatus.Success("Backup exported successfully")
                loadBackupHistory()
            } catch (e: Exception) {
                _backupStatus.value = BackupStatus.Error(
                    "Export failed: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        val database = db ?: run {
            _backupStatus.value = BackupStatus.Error("Database not available")
            return
        }
        _backupStatus.value = BackupStatus.InProgress
        viewModelScope.launch {
            try {
                JsonBackupManager.importFromUri(database, context, uri)
                _backupStatus.value = BackupStatus.Success("Backup restored successfully")
                loadBackupHistory()
            } catch (e: Exception) {
                _backupStatus.value = BackupStatus.Error(
                    "Restore failed: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun clearBackupStatus() {
        _backupStatus.value = BackupStatus.Idle
    }

    // ─── Vehicles (reactive) ─────────────────────────────────────────────────

    val vehicles: StateFlow<List<Vehicle>> = vehicleDao.observeActiveVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allVehicles: StateFlow<List<Vehicle>> = vehicleDao.observeAllVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archivedVehicles: StateFlow<List<Vehicle>> = vehicleDao.observeArchivedVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addVehicle(vehicle: Vehicle) {
        viewModelScope.launch { vehicleDao.insertVehicle(vehicle) }
    }

    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch { vehicleDao.updateVehicle(vehicle) }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch { vehicleDao.deleteVehicle(vehicle) }
    }

    fun archiveVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleDao.updateVehicle(
                vehicle.copy(isArchived = true, archivedAt = System.currentTimeMillis())
            )
        }
    }

    fun unarchiveVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleDao.updateVehicle(
                vehicle.copy(isArchived = false, archivedAt = null)
            )
        }
    }

    // ─── Events (reactive per vehicle) ───────────────────────────────────────

    private val _selectedVehicleId = MutableStateFlow(-1)

    val events: StateFlow<List<VehicleEvent>> = _selectedVehicleId
        .flatMapLatest { id ->
            if (id > 0) eventDao.observeEventsForVehicle(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectVehicleForEvents(vehicleId: Int) {
        _selectedVehicleId.value = vehicleId
    }

    fun addEvent(event: VehicleEvent) {
        viewModelScope.launch { eventDao.insertEvent(event) }
    }

    fun updateEvent(event: VehicleEvent) {
        viewModelScope.launch { eventDao.updateEvent(event) }
    }

    fun deleteEvent(event: VehicleEvent) {
        viewModelScope.launch { eventDao.deleteEvent(event) }
    }

    // ─── Expenses ────────────────────────────────────────────────────────────

    suspend fun getExpensesForVehicleInRange(
        vehicleId: Int, startDate: Long, endDate: Long
    ): List<VehicleEvent> {
        return eventDao.getExpensesForVehicleInRange(vehicleId, startDate, endDate)
    }

    suspend fun getTotalExpensesForVehicleInRange(
        vehicleId: Int, startDate: Long, endDate: Long
    ): Double? {
        return eventDao.getTotalExpensesForVehicleInRange(vehicleId, startDate, endDate)
    }

    // ─── Fuel Analytics ──────────────────────────────────────────────────────

    data class FuelStats(
        val avgCostPerKm: Double?,
        val totalFuelCost: Double,
        val totalDistance: Double?,
        val fillCount: Int
    )

    suspend fun calculateFuelStats(vehicleId: Int): FuelStats {
        val fuelEvents = eventDao.getFuelEventsAsc(vehicleId)
        val withOdometer = fuelEvents.filter { it.odometer != null && it.odometer > 0 }

        var totalFuelCost = 0.0
        fuelEvents.forEach { totalFuelCost += it.cost ?: 0.0 }

        if (withOdometer.size < 2) {
            return FuelStats(
                avgCostPerKm = null,
                totalFuelCost = totalFuelCost,
                totalDistance = null,
                fillCount = fuelEvents.size
            )
        }

        var totalDistance = 0.0
        var totalCostBetweenFills = 0.0
        var validPairs = 0

        for (i in 1 until withOdometer.size) {
            val prev = withOdometer[i - 1]
            val curr = withOdometer[i]
            val dist = curr.odometer!! - prev.odometer!!
            if (dist > 0) {
                totalDistance += dist
                totalCostBetweenFills += curr.cost ?: 0.0
                validPairs++
            }
        }

        val avgCostPerKm = if (totalDistance > 0) totalCostBetweenFills / totalDistance else null

        return FuelStats(
            avgCostPerKm = avgCostPerKm,
            totalFuelCost = totalFuelCost,
            totalDistance = if (totalDistance > 0) totalDistance else null,
            fillCount = fuelEvents.size
        )
    }

    // ─── Category Breakdown ──────────────────────────────────────────────────

    suspend fun getCategoryBreakdown(
        vehicleId: Int, startDate: Long, endDate: Long
    ): List<CategoryExpense> {
        return eventDao.getExpensesByCategory(vehicleId, startDate, endDate)
            .sortedByDescending { it.totalCost }
    }

    // ─── EventMeta ───────────────────────────────────────────────────────────

    private val _meta = MutableStateFlow<List<EventMeta>>(emptyList())
    val meta: StateFlow<List<EventMeta>> = _meta

    fun fetchMetaForEvent(eventId: Int) {
        viewModelScope.launch { _meta.value = metaDao.getMetaForEvent(eventId) }
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

    // ─── User Profile ────────────────────────────────────────────────────────

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
                appLockEnabled = existing?.appLockEnabled ?: false,
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

    // ─── Google Drive ─────────────────────────────────────────────────────────

    fun checkExistingSignIn(context: Context) {
        _driveAccount.value = GoogleSignIn.getLastSignedInAccount(context)
    }

    fun getSignInIntent(context: Context): Intent {
        return DriveBackupManager.getGoogleSignInClient(context).signInIntent
    }

    fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            _driveAccount.value = account
            viewModelScope.launch {
                val email = account?.email
                if (email != null) {
                    val profile = userProfileDao.getProfile()
                    if (profile != null) {
                        userProfileDao.upsert(
                            profile.copy(
                                driveAccountEmail = email,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        } catch (e: ApiException) {
            _syncStatus.value = SyncStatus.Error("Sign-in failed: ${e.statusCode}")
        }
    }

    fun signOutFromGoogle(context: Context) {
        DriveBackupManager.getGoogleSignInClient(context).signOut().addOnCompleteListener {
            _driveAccount.value = null
            _driveBackups.value = emptyList()
            viewModelScope.launch {
                val profile = userProfileDao.getProfile()
                if (profile != null) {
                    userProfileDao.upsert(
                        profile.copy(
                            driveAccountEmail = null,
                            backupEnabled = false,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    fun performDriveBackup(context: Context) {
        val database = db ?: run {
            _syncStatus.value = SyncStatus.Error("Database not available")
            return
        }
        _syncStatus.value = SyncStatus.InProgress
        viewModelScope.launch {
            try {
                DriveBackupManager.uploadBackup(context, database)
                _syncStatus.value = SyncStatus.Success("Backup uploaded to Drive")
                loadBackupHistory()
                listDriveBackups(context)
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error(
                    "Drive backup failed: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun performDriveRestore(context: Context, fileId: String) {
        val database = db ?: run {
            _syncStatus.value = SyncStatus.Error("Database not available")
            return
        }
        _syncStatus.value = SyncStatus.InProgress
        viewModelScope.launch {
            try {
                DriveBackupManager.restoreFromDrive(context, database, fileId)
                _syncStatus.value = SyncStatus.Success("Data restored from Drive")
                loadBackupHistory()
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error(
                    "Drive restore failed: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun listDriveBackups(context: Context) {
        viewModelScope.launch {
            try {
                val credential = DriveBackupManager.getCredential(context) ?: return@launch
                _driveBackups.value = DriveBackupManager.listBackups(credential)
            } catch (_: Exception) {
                _driveBackups.value = emptyList()
            }
        }
    }

    fun toggleAutoBackup(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            val profile = userProfileDao.getProfile() ?: return@launch
            userProfileDao.upsert(
                profile.copy(backupEnabled = enabled, updatedAt = System.currentTimeMillis())
            )
            if (enabled) {
                DriveAutoBackupWorker.schedule(context)
            } else {
                DriveAutoBackupWorker.cancel(context)
            }
        }
    }

    fun clearSyncStatus() {
        _syncStatus.value = SyncStatus.Idle
    }
}
