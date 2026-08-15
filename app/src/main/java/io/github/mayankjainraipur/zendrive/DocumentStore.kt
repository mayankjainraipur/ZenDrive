package io.github.mayankjainraipur.zendrive

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns every file the user attaches as a document.
 *
 * A picked file is copied into app-private storage straight away, so the record survives the
 * source file being moved or deleted — and because the copies travel inside the backup archive,
 * it survives a move to a new phone too. Rows created before this existed keep only their
 * original SAF URI in [VehicleDocument.storageUri]; [adoptLegacyDocuments] rescues those.
 */
object DocumentStore {

    /** Bucket name; also the directory under filesDir. */
    const val BUCKET = "documents"

    /** Path prefix for document entries inside a backup archive. */
    const val ARCHIVE_PREFIX = "documents/"

    fun dir(context: Context): File = OwnedFiles.dir(context, BUCKET)

    fun fileFor(context: Context, localFileName: String): File =
        OwnedFiles.fileFor(context, BUCKET, localFileName)

    /** Copies [uri] into app-private storage and returns the generated local file name. */
    suspend fun copyIn(context: Context, uri: Uri, displayName: String?): String =
        OwnedFiles.copyIn(context, BUCKET, uri, displayName)

    /** Writes a document restored out of a backup archive. */
    fun writeRestored(context: Context, localFileName: String, bytes: ByteArray) {
        fileFor(context, localFileName).writeBytes(bytes)
    }

    fun delete(context: Context, localFileName: String?) =
        OwnedFiles.delete(context, BUCKET, localFileName)

    /** A content URI another app can read, granted per-intent. */
    fun shareUri(context: Context, localFileName: String): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            fileFor(context, localFileName)
        )

    /** Opens the owned copy when there is one, else falls back to a legacy row's SAF URI. */
    fun viewIntent(context: Context, doc: VehicleDocument): Intent {
        val local = doc.localFileName
        val uri = if (local != null) shareUri(context, local) else Uri.parse(doc.storageUri)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, doc.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Guards against zip-slip: a backup is just a file, and it may not have come from us.
     * Returns the bare file name, or null if the entry is not a plain document.
     */
    fun sanitizeArchiveEntry(entryName: String): String? {
        if (!entryName.startsWith(ARCHIVE_PREFIX)) return null
        val name = entryName.removePrefix(ARCHIVE_PREFIX)
        if (name.isBlank() || name.contains('/') || name.contains('\\') || name.contains("..")) {
            return null
        }
        return name
    }

    /** Deletes stored files no row points at — the backstop for cascaded vehicle deletes. */
    suspend fun pruneOrphans(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        OwnedFiles.prune(context, BUCKET, db.vehicleDocumentDao().getAllLocalFileNames().toHashSet())
    }

    /**
     * Takes a private copy of any document still holding only a SAF URI, while that URI is
     * readable. Best-effort and silent: a source file that is already gone stays as it was.
     */
    suspend fun adoptLegacyDocuments(context: Context, db: AppDatabase) =
        withContext(Dispatchers.IO) {
            val dao = db.vehicleDocumentDao()
            for (doc in dao.getDocumentsMissingLocalCopy()) {
                val uri = runCatching { Uri.parse(doc.storageUri) }.getOrNull() ?: continue
                val copied = runCatching { copyIn(context, uri, doc.fileName) }.getOrNull() ?: continue
                dao.update(
                    doc.copy(localFileName = copied, updatedAt = System.currentTimeMillis())
                )
            }
        }

}
