package io.github.mayankjainraipur.zendrive

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
    /** Null for a personal document — a driving licence belongs to the owner, not a vehicle. */
    val vehicleId: Int? = null,
    /** Short label shown in UI */
    val title: String,
    /** e.g. insurance, registration, puc, invoice, warranty, other */
    val documentType: String,
    /** Original file name from picker */
    val fileName: String,
    val mimeType: String? = null,
    /**
     * The SAF URI the file was originally picked from. Kept for reference only — it may point at
     * a file that has since moved or been deleted. Read [localFileName] instead.
     */
    val storageUri: String,
    /**
     * File name of this document's private copy inside [DocumentStore]. Null only for rows
     * created before documents were copied in, and for those whose source URI was already dead.
     */
    val localFileName: String? = null,
    val fileSizeBytes: Long? = null,
    /** Optional expiry (e.g. insurance / PUC), epoch millis */
    val expiresAt: Long? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)