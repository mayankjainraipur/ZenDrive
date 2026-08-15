package io.github.mayankjainraipur.zendrive

import kotlinx.serialization.Serializable

@Serializable
data class BackupBundle(
    val schemaVersion: Int = 3,
    val appVersion: String,
    val exportedAt: Long,
    val profile: BackupProfile?,
    val vehicles: List<BackupVehicle>,
    val events: List<BackupEvent>,
    val eventMeta: List<BackupEventMeta>,
    val reminders: List<BackupReminder>,
    val documents: List<BackupDocument>,
    /** Hand-entered readings only; event-derived ones are rebuilt by OdometerSync on restore. */
    val odometerLogs: List<BackupOdometerLog> = emptyList(),
    /** Photos and receipts; the files themselves travel under `attachments/` in the archive. */
    val attachments: List<BackupAttachment> = emptyList()
)

@Serializable
data class BackupAttachment(
    /** Owning event's pre-import id; remapped on restore like every other reference. */
    val eventOriginalId: Int,
    val localFileName: String,
    val mimeType: String? = null,
    val caption: String? = null,
    val createdAt: Long
) {
    fun toEntity(newEventId: Int): Attachment = Attachment(
        id = 0,
        ownerType = Attachment.OWNER_EVENT,
        ownerId = newEventId,
        localFileName = localFileName,
        mimeType = mimeType,
        caption = caption,
        createdAt = createdAt
    )

    companion object {
        fun fromEntity(e: Attachment) = BackupAttachment(
            eventOriginalId = e.ownerId,
            localFileName = e.localFileName,
            mimeType = e.mimeType,
            caption = e.caption,
            createdAt = e.createdAt
        )
    }
}

@Serializable
data class BackupOdometerLog(
    val vehicleOriginalId: Int,
    val reading: Double,
    val recordedAt: Long,
    val note: String? = null,
    val createdAt: Long
) {
    fun toEntity(newVehicleId: Int): OdometerLog = OdometerLog(
        id = 0,
        vehicleId = newVehicleId,
        reading = reading,
        recordedAt = recordedAt,
        source = OdometerLog.SOURCE_MANUAL,
        eventId = null,
        note = note,
        createdAt = createdAt
    )

    companion object {
        fun fromEntity(e: OdometerLog) = BackupOdometerLog(
            vehicleOriginalId = e.vehicleId,
            reading = e.reading,
            recordedAt = e.recordedAt,
            note = e.note,
            createdAt = e.createdAt
        )
    }
}

@Serializable
data class BackupProfile(
    val displayName: String,
    val email: String,
    val mobileNumber: String? = null,
    val preferredCurrencyCode: String,
    val distanceUnit: String = "km",
    val dateFormatPattern: String = "dd MMM yyyy",
    val themeMode: String = "dark",
    val reminderLeadDays: Int = 3,
    val appLockEnabled: Boolean = false,
    val backupEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val lastRestoreAt: Long? = null,
    val driveAccountEmail: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toEntity(): UserProfile = UserProfile(
        id = 1,
        displayName = displayName,
        email = email,
        mobileNumber = mobileNumber,
        preferredCurrencyCode = preferredCurrencyCode,
        distanceUnit = distanceUnit,
        dateFormatPattern = dateFormatPattern,
        themeMode = themeMode,
        reminderLeadDays = reminderLeadDays,
        appLockEnabled = appLockEnabled,
        backupEnabled = backupEnabled,
        lastBackupAt = lastBackupAt,
        lastRestoreAt = lastRestoreAt,
        driveAccountEmail = driveAccountEmail,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromEntity(e: UserProfile) = BackupProfile(
            displayName = e.displayName,
            email = e.email,
            mobileNumber = e.mobileNumber,
            preferredCurrencyCode = e.preferredCurrencyCode,
            distanceUnit = e.distanceUnit,
            dateFormatPattern = e.dateFormatPattern,
            themeMode = e.themeMode,
            reminderLeadDays = e.reminderLeadDays,
            appLockEnabled = e.appLockEnabled,
            backupEnabled = e.backupEnabled,
            lastBackupAt = e.lastBackupAt,
            lastRestoreAt = e.lastRestoreAt,
            driveAccountEmail = e.driveAccountEmail,
            createdAt = e.createdAt,
            updatedAt = e.updatedAt
        )
    }
}

@Serializable
data class BackupVehicle(
    val originalId: Int,
    val name: String,
    val vehicleNumber: String,
    val type: String,
    val fuelType: String,
    val brand: String,
    val model: String,
    val year: Int,
    val purchaseDate: Long? = null,
    val odometerReading: Double = 0.0,
    val notes: String? = null,
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toEntity(): Vehicle = Vehicle(
        id = 0,
        name = name,
        vehicleNumber = vehicleNumber,
        type = type,
        fuelType = fuelType,
        brand = brand,
        model = model,
        year = year,
        purchaseDate = purchaseDate,
        odometerReading = odometerReading,
        notes = notes,
        isArchived = isArchived,
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromEntity(e: Vehicle) = BackupVehicle(
            originalId = e.id,
            name = e.name,
            vehicleNumber = e.vehicleNumber,
            type = e.type,
            fuelType = e.fuelType,
            brand = e.brand,
            model = e.model,
            year = e.year,
            purchaseDate = e.purchaseDate,
            odometerReading = e.odometerReading,
            notes = e.notes,
            isArchived = e.isArchived,
            archivedAt = e.archivedAt,
            createdAt = e.createdAt,
            updatedAt = e.updatedAt
        )
    }
}

@Serializable
data class BackupEvent(
    val originalId: Int,
    val vehicleOriginalId: Int,
    val eventType: String,
    val title: String,
    val description: String? = null,
    val date: Long,
    val odometer: Double? = null,
    val cost: Double? = null,
    val fuelVolume: Double? = null,
    val pricePerUnit: Double? = null,
    val isFullTank: Boolean = false,
    val nextDueDate: Long? = null,
    val createdAt: Long
) {
    fun toEntity(newVehicleId: Int): VehicleEvent = VehicleEvent(
        id = 0,
        vehicleId = newVehicleId,
        eventType = eventType,
        title = title,
        description = description,
        date = date,
        odometer = odometer,
        cost = cost,
        fuelVolume = fuelVolume,
        pricePerUnit = pricePerUnit,
        isFullTank = isFullTank,
        nextDueDate = nextDueDate,
        createdAt = createdAt
    )

    companion object {
        fun fromEntity(e: VehicleEvent) = BackupEvent(
            originalId = e.id,
            vehicleOriginalId = e.vehicleId,
            eventType = e.eventType,
            title = e.title,
            description = e.description,
            date = e.date,
            odometer = e.odometer,
            cost = e.cost,
            fuelVolume = e.fuelVolume,
            pricePerUnit = e.pricePerUnit,
            isFullTank = e.isFullTank,
            nextDueDate = e.nextDueDate,
            createdAt = e.createdAt
        )
    }
}

@Serializable
data class BackupEventMeta(
    val eventOriginalId: Int,
    val key: String,
    val value: String
) {
    fun toEntity(newEventId: Int): EventMeta = EventMeta(
        id = 0,
        eventId = newEventId,
        key = key,
        value = value
    )

    companion object {
        fun fromEntity(e: EventMeta) = BackupEventMeta(
            eventOriginalId = e.eventId,
            key = e.key,
            value = e.value
        )
    }
}

@Serializable
data class BackupReminder(
    val vehicleOriginalId: Int,
    val eventOriginalId: Int? = null,
    val title: String,
    val description: String? = null,
    val reminderType: String,
    val dueAt: Long,
    val repeatRule: String = "none",
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val notifyAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toEntity(newVehicleId: Int, newEventId: Int?): Reminder = Reminder(
        id = 0,
        vehicleId = newVehicleId,
        eventId = newEventId,
        title = title,
        description = description,
        reminderType = reminderType,
        dueAt = dueAt,
        repeatRule = repeatRule,
        isCompleted = isCompleted,
        completedAt = completedAt,
        notifyAt = notifyAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromEntity(e: Reminder) = BackupReminder(
            vehicleOriginalId = e.vehicleId,
            eventOriginalId = e.eventId,
            title = e.title,
            description = e.description,
            reminderType = e.reminderType,
            dueAt = e.dueAt,
            repeatRule = e.repeatRule,
            isCompleted = e.isCompleted,
            completedAt = e.completedAt,
            notifyAt = e.notifyAt,
            createdAt = e.createdAt,
            updatedAt = e.updatedAt
        )
    }
}

@Serializable
data class BackupDocument(
    /** Null for a personal document, which belongs to the owner rather than a vehicle. */
    val vehicleOriginalId: Int? = null,
    val title: String,
    val documentType: String,
    val fileName: String,
    val mimeType: String? = null,
    val storageUri: String,
    /** Names the copy carried alongside this bundle under `documents/` in the archive. */
    val localFileName: String? = null,
    val fileSizeBytes: Long? = null,
    val expiresAt: Long? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toEntity(newVehicleId: Int?): VehicleDocument = VehicleDocument(
        id = 0,
        vehicleId = newVehicleId,
        title = title,
        documentType = documentType,
        fileName = fileName,
        mimeType = mimeType,
        storageUri = storageUri,
        localFileName = localFileName,
        fileSizeBytes = fileSizeBytes,
        expiresAt = expiresAt,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromEntity(e: VehicleDocument) = BackupDocument(
            vehicleOriginalId = e.vehicleId,
            title = e.title,
            documentType = e.documentType,
            fileName = e.fileName,
            mimeType = e.mimeType,
            storageUri = e.storageUri,
            localFileName = e.localFileName,
            fileSizeBytes = e.fileSizeBytes,
            expiresAt = e.expiresAt,
            notes = e.notes,
            createdAt = e.createdAt,
            updatedAt = e.updatedAt
        )
    }
}