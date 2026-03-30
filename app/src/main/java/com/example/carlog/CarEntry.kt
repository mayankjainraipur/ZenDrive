package com.example.carlog

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "car_logs")
data class CarEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val type: String, // e.g., 'Fuel', 'Service', 'Repair'
    val mileage: Int,
    val cost: Double,
    val notes: String?
)
