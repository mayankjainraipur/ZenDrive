package io.github.mayankjainraipur.zendrive

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A photo or receipt attached to something — currently an event, with vehicles supported by the
 * schema for later.
 *
 * Deliberately polymorphic rather than one table per owner: a receipt is a receipt whether it
 * hangs off a fuel entry or a vehicle, and three near-identical tables would be worse.
 * The trade-off is no foreign key, so [AttachmentStore.pruneOrphans] does the cleanup a cascade
 * would otherwise handle.
 */
@androidx.room.Entity(
    tableName = "attachment",
    indices = [androidx.room.Index(value = ["ownerType", "ownerId"])]
)
data class Attachment(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** [OWNER_EVENT] or [OWNER_VEHICLE]. */
    val ownerType: String,
    val ownerId: Int,
    /** File name inside the attachments bucket. */
    val localFileName: String,
    val mimeType: String? = null,
    val caption: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val OWNER_EVENT = "event"
        const val OWNER_VEHICLE = "vehicle"
    }
}

/**
 * Owns attachment files, in a bucket of their own.
 *
 * Separate from documents because each bucket is pruned against its own table: a file in the
 * documents directory that no document row references is deleted, so attachments kept alongside
 * would be destroyed by the next document prune.
 */
object AttachmentStore {

    const val BUCKET = "attachments"
    const val ARCHIVE_PREFIX = "attachments/"

    fun fileFor(context: Context, localFileName: String): File =
        OwnedFiles.fileFor(context, BUCKET, localFileName)

    suspend fun copyIn(context: Context, uri: Uri, displayName: String?): String =
        OwnedFiles.copyIn(context, BUCKET, uri, displayName)

    fun writeRestored(context: Context, localFileName: String, bytes: ByteArray) {
        fileFor(context, localFileName).writeBytes(bytes)
    }

    fun delete(context: Context, localFileName: String?) =
        OwnedFiles.delete(context, BUCKET, localFileName)

    fun sanitizeArchiveEntry(entryName: String): String? =
        OwnedFiles.sanitizeArchiveEntry(entryName, ARCHIVE_PREFIX)

    fun shareUri(context: Context, localFileName: String): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            fileFor(context, localFileName)
        )

    fun viewIntent(context: Context, attachment: Attachment): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                shareUri(context, attachment.localFileName),
                attachment.mimeType ?: "image/*"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /**
     * Deletes files no row points at. Also does the work a foreign key would: attachments whose
     * owning event no longer exists are removed, since the polymorphic owner cannot cascade.
     */
    suspend fun pruneOrphans(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        val dao = db.attachmentDao()

        val liveEventIds = db.vehicleEventDao().getAllEventIds().toHashSet()
        val liveVehicleIds = db.vehicleDao().getAllVehicles().map { it.id }.toHashSet()

        for (attachment in dao.getAll()) {
            val ownerAlive = when (attachment.ownerType) {
                Attachment.OWNER_EVENT -> attachment.ownerId in liveEventIds
                Attachment.OWNER_VEHICLE -> attachment.ownerId in liveVehicleIds
                else -> false
            }
            if (!ownerAlive) dao.delete(attachment)
        }

        OwnedFiles.prune(context, BUCKET, dao.getAllLocalFileNames().toHashSet())
    }
}
