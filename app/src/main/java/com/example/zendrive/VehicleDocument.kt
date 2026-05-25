package com.example.zendrive

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A file the user attached to a vehicle (insurance PDF, RC scan, invoices, etc.).
 */
@Entity(
    tableName = "vehicle_documents",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("vehicleId"),
        Index(value = ["vehicleId", "documentType"])
    ]
)
data class VehicleDocument(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleId: Int,
    /** Short label shown in UI */
    val title: String,
    /** e.g. insurance, registration, puc, invoice, warranty, other */
    val documentType: String,
    /** Original file name from picker */
    val fileName: String,
    val mimeType: String? = null,
    /**
     * Local reference: content URI string or app-private path. Not validated here.
     */
    val storageUri: String,
    val fileSizeBytes: Long? = null,
    /** Optional expiry (e.g. insurance / PUC), epoch millis */
    val expiresAt: Long? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
