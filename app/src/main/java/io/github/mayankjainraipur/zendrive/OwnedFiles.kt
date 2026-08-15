package io.github.mayankjainraipur.zendrive

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Shared plumbing for files the app owns outright, kept in one place so the rules that matter —
 * how names are generated and how archive entries are validated — cannot drift between callers.
 *
 * Files live in separate directories per kind ("buckets"), because each is pruned against its own
 * table: anything in the documents directory that no document row points at is deleted, so an
 * attachment stored alongside would be destroyed by the next prune.
 */
internal object OwnedFiles {

    fun dir(context: Context, bucket: String): File =
        File(context.filesDir, bucket).apply { if (!exists()) mkdirs() }

    fun fileFor(context: Context, bucket: String, name: String): File =
        File(dir(context, bucket), name)

    /** Copies [uri] into the bucket and returns the generated file name. */
    suspend fun copyIn(context: Context, bucket: String, uri: Uri, displayName: String?): String =
        withContext(Dispatchers.IO) {
            val target = File(dir(context, bucket), newFileName(context, uri, displayName))
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Cannot read the selected file")
            target.name
        }

    fun delete(context: Context, bucket: String, name: String?) {
        if (name.isNullOrBlank()) return
        runCatching { fileFor(context, bucket, name).delete() }
    }

    /**
     * Guards against zip-slip: a backup is just a file and may not have come from us, so an entry
     * named `../../databases/zendrive_db` must never be honoured. Returns the bare file name, or
     * null when the entry does not belong to [prefix] or tries to escape it.
     */
    fun sanitizeArchiveEntry(entryName: String, prefix: String): String? {
        if (!entryName.startsWith(prefix)) return null
        val name = entryName.removePrefix(prefix)
        if (name.isBlank() || name.contains('/') || name.contains('\\') || name.contains("..")) {
            return null
        }
        return name
    }

    /** Deletes everything in the bucket that [referenced] does not mention. */
    fun prune(context: Context, bucket: String, referenced: Set<String>) {
        dir(context, bucket).listFiles()?.forEach { file ->
            if (file.name !in referenced) file.delete()
        }
    }

    private fun newFileName(context: Context, uri: Uri, displayName: String?): String {
        val ext = displayName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(context.contentResolver.getType(uri))
        val stem = UUID.randomUUID().toString().replace("-", "")
        return if (ext.isNullOrBlank()) stem else "$stem.${ext.lowercase(Locale.US)}"
    }
}
