package com.example.zendrive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ViewModelFactory(
    private val vehicleDao: VehicleDao,
    private val eventDao: VehicleEventDao,
    private val metaDao: EventMetaDao,
    private val userProfileDao: UserProfileDao,
    private val db: AppDatabase? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LogViewModel::class.java)) {
            return LogViewModel(vehicleDao, eventDao, metaDao, userProfileDao, db) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
