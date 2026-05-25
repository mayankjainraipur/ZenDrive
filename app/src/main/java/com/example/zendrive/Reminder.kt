package com.example.zendrive

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Scheduled reminder independent of [VehicleEvent], for richer notification / recurrence UX later.
 */
@Entity(
    tableName = "reminder",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VehicleEvent::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("vehicleId"),
        Index("eventId"),
        Index(value = ["dueAt"]),
        Index(value = ["vehicleId", "dueAt"])
    ]
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleId: Int,
    /** Optional link to an existing maintenance/event row */
    val eventId: Int? = null,
    val title: String,
    val description: String? = null,
    /** e.g. service, insurance, tax, document_expiry, custom */
    val reminderType: String,
    /** When the reminder is due, epoch millis */
    val dueAt: Long,
    /** e.g. none, daily, weekly, monthly, yearly */
    val repeatRule: String = "none",
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    /** When to notify (if null, use [dueAt]) */
    val notifyAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
