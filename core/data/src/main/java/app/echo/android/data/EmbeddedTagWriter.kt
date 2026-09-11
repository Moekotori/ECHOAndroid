package app.echo.android.data

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.system.Os
import android.util.Log
import androidx.core.content.ContextCompat
import app.echo.android.model.library.LibrarySource
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EmbeddedTagWriter(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val writeMutex = Mutex()

    internal fun fieldsForWrite(
        track: LibraryTrackEntity,
        lyricsText: String? = null,
        artworkUri: String? = null,
    ): AudioTagFields {
        val encoded = artworkUri?.takeIf { it.isNotBlank() }?.let { uri ->
            runCatching { EmbeddedArtworkEncoder.encode(resolver, Uri.parse(uri)) }.getOrNull()
        }
        return track.toAudioTagFields().copy(
            lyrics = lyricsText,
            artworkBytes = encoded?.bytes,
            artworkMime = encoded?.mime,
        )
    }

    internal suspend fun write(
        track: LibraryTrackEntity,
        fields: AudioTagFields,
    ): EmbeddedTagWriteResult =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                writeLocked(track, fields)
            }
        }

    private fun writeLocked(
        track: LibraryTrackEntity,
        fields: AudioTagFields,
    ): EmbeddedTagWriteResult {
        if (!LibraryScanPolicy.isLocalLibrarySource(track.source) &&
            !LibrarySource(track.source).isLocalAudioFile
        ) {
            return EmbeddedTagWriteResult.NotLocal
        }
        val uri = track.contentUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: return EmbeddedTagWriteResult.Failed
        if (uri.scheme != ContentResolver.SCHEME_CONTENT && uri.scheme != ContentResolver.SCHEME_FILE) {
            return EmbeddedTagWriteResult.NotLocal
        }
        val sizeBytes = track.sizeBytes.takeIf { it > 0 } ?: querySize(uri) ?: 0L
        if (sizeBytes > MAX_REWRITE_BYTES) return EmbeddedTagWriteResult.Failed
        if (!hasEnoughCacheSpace(sizeBytes)) return EmbeddedTagWriteResult.Failed

        val format = peekFormat(uri, track.mimeType)
        if (format == PeekFormat.Unsupported) return EmbeddedTagWriteResult.UnsupportedFormat
        if (format == PeekFormat.Unreadable) return EmbeddedTagWriteResult.Failed

        val access = writeAccess(uri, track.source)
        if (access != null) return access

        val cacheDir = File(appContext.cacheDir, TAG_CACHE_DIR).apply { mkdirs() }
        val original = File(cacheDir, "original-${track.id.hashCode()}")
        val tagged = File(cacheDir, "tagged-${track.id.hashCode()}")
        var keepBackup = false
        return try {
            copyUriToFile(uri, original)
            when (
                AudioFileTagRewriter.rewriteFile(
                    source = original,
                    destination = tagged,
                    fields = fields,
                    mimeType = track.mimeType,
                )
            ) {
                AudioTagRewriteStatus.UnsupportedFormat -> EmbeddedTagWriteResult.UnsupportedFormat
                AudioTagRewriteStatus.InvalidSource -> EmbeddedTagWriteResult.Failed
                AudioTagRewriteStatus.Written -> {
                    if (!fileFieldsMatch(tagged, fields)) {
                        EmbeddedTagWriteResult.Failed
                    } else {
                        when (replaceWithRestore(uri, tagged, original, fields)) {
                            EmbeddedTagReplaceStatus.Replaced -> {
                                notifyMediaStore(uri, tagged, fields, track)
                                EmbeddedTagWriteResult.Written(
                                    sizeBytes = tagged.length(),
                                    dateModifiedSeconds = System.currentTimeMillis() / 1000L,
                                )
                            }
                            EmbeddedTagReplaceStatus.Restored -> EmbeddedTagWriteResult.Failed
                            EmbeddedTagReplaceStatus.BackupKept -> {
                                keepBackup = true
                                EmbeddedTagWriteResult.Failed
                            }
                        }
                    }
                }
            }
        } catch (error: SecurityException) {
            mediaStoreConsentOrFailed(uri, track.source, error)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to write embedded tags for ${track.id}.", error)
            EmbeddedTagWriteResult.Failed
        } finally {
            tagged.delete()
            if (keepBackup) {
                EmbeddedTagBackup.preserveOriginal(
                    original = original,
                    cacheDir = cacheDir,
                    trackKey = track.id.hashCode().toString(),
                )
            } else {
                original.delete()
            }
        }
    }

    private fun peekFormat(uri: Uri, mimeType: String?): PeekFormat {
        val header = ByteArray(10)
        val read = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                var offset = 0
                while (offset < header.size) {
                    val n = input.read(header, offset, header.size - offset)
                    if (n < 0) break
                    offset += n
                }
                offset
            }
        }.getOrNull() ?: return PeekFormat.Unreadable
        if (read <= 0) return PeekFormat.Unreadable
        val probe = header
        return when {
            probe[0] == 'I'.code.toByte() && probe[1] == 'D'.code.toByte() && probe[2] == '3'.code.toByte() ->
                PeekFormat.Supported
            probe[0] == 'f'.code.toByte() && probe[1] == 'L'.code.toByte() &&
                probe[2] == 'a'.code.toByte() && probe[3] == 'C'.code.toByte() ->
                PeekFormat.Supported
            looksLikeMp3(probe, read, mimeType) -> PeekFormat.Supported
            looksLikeRiff(probe, read) || LibraryWavTagPolicy.isWavContainer(mimeType) -> PeekFormat.Supported
            looksLikeUnsupportedContainer(probe, read) -> PeekFormat.Unsupported
            mimeType.orEmpty().lowercase().let {
                it.contains("mpeg") || it.contains("flac") || it.contains("mp3") || it.contains("wav")
            } -> PeekFormat.Supported
            else -> PeekFormat.Unsupported
        }
    }

    private fun writeAccess(uri: Uri, source: String): EmbeddedTagWriteResult? {
        if (canOpenReadWrite(uri) || hasPersistedWriteAccess(uri)) return null
        val isMediaStore = source == LibrarySource.MediaStore.id ||
            uri.authority?.contains("media") == true
        return when {
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED ->
                EmbeddedTagWriteResult.NeedsStoragePermission
            isMediaStore && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                EmbeddedTagWriteResult.NeedsMediaStoreConsent(uri.toString())
            else -> null
        }
    }

    private fun mediaStoreConsentOrFailed(
        uri: Uri,
        source: String,
        error: SecurityException,
    ): EmbeddedTagWriteResult {
        val isMediaStore = source == LibrarySource.MediaStore.id ||
            uri.authority?.contains("media") == true
        if (isMediaStore && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                error is RecoverableSecurityException
            ) {
                error.userAction.actionIntent.intentSender
            } else {
                null
            }
            return EmbeddedTagWriteResult.NeedsMediaStoreConsent(uri.toString(), sender)
        }
        Log.w(TAG, "Write access denied for $uri.", error)
        return EmbeddedTagWriteResult.Failed
    }

    private fun canOpenReadWrite(uri: Uri): Boolean =
        runCatching {
            resolver.openFileDescriptor(uri, "rw")?.close() ?: return false
        }.isSuccess

    private fun hasPersistedWriteAccess(uri: Uri): Boolean {
        if (appContext.checkUriPermission(
                uri,
                Process.myPid(),
                Process.myUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return resolver.persistedUriPermissions.any { permission ->
            permission.isWritePermission && coversUri(permission.uri, uri)
        }
    }

    private fun coversUri(granted: Uri, target: Uri): Boolean {
        if (granted == target) return true
        if (granted.authority != target.authority) return false
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(granted) }.getOrNull() ?: return false
        val documentId = runCatching { DocumentsContract.getDocumentId(target) }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(target) }.getOrNull()
            ?: return false
        return documentId == treeId || documentId.startsWith("$treeId/")
    }

    private fun copyUriToFile(uri: Uri, destination: File) {
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output, COPY_BUFFER)
            }
        } ?: error("Unable to open $uri")
    }

    private fun replaceWithRestore(
        uri: Uri,
        tagged: File,
        original: File,
        fields: AudioTagFields,
    ): EmbeddedTagReplaceStatus {
        val fileTarget = writableLocalFile(uri)
        try {
            if (fileTarget != null) {
                EmbeddedTagFileReplace.replace(fileTarget, tagged)
            } else {
                replaceUriContents(uri, tagged)
            }
            if (liveFieldsMatch(uri, fields)) return EmbeddedTagReplaceStatus.Replaced
            Log.w(TAG, "Embedded tag verify failed for $uri, restoring original.")
        } catch (error: SecurityException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Unable to replace $uri, restoring original.", error)
        }
        return restoreOriginal(uri, fileTarget, original)
    }

    private fun restoreOriginal(
        uri: Uri,
        fileTarget: File?,
        original: File,
    ): EmbeddedTagReplaceStatus {
        return try {
            if (fileTarget != null) {
                EmbeddedTagFileReplace.replace(fileTarget, original)
            } else {
                replaceUriContents(uri, original)
            }
            EmbeddedTagReplaceStatus.Restored
        } catch (error: SecurityException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Unable to restore $uri. Backup kept at ${original.absolutePath}.", error)
            EmbeddedTagReplaceStatus.BackupKept
        }
    }

    private fun fileFieldsMatch(file: File, fields: AudioTagFields): Boolean {
        val verified = runCatching {
            FileInputStream(file).use { AudioFileTagRewriter.readFields(it) }
        }.getOrNull()
        return verified?.title == fields.title && verified.artist == fields.artist
    }

    private fun liveFieldsMatch(uri: Uri, fields: AudioTagFields): Boolean {
        val live = runCatching {
            resolver.openInputStream(uri)?.use { AudioFileTagRewriter.readFields(it) }
        }.getOrNull()
        return live?.title == fields.title && live.artist == fields.artist
    }

    private fun writableLocalFile(uri: Uri): File? {
        if (uri.scheme != ContentResolver.SCHEME_FILE) return null
        val path = uri.path?.takeIf { it.isNotBlank() } ?: return null
        val file = File(path)
        return file.takeIf { it.isFile && it.canWrite() }
    }

    private fun replaceUriContents(uri: Uri, tagged: File) {
        val length = tagged.length()
        val replaced = runCatching {
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { out ->
                    FileInputStream(tagged).use { input -> input.copyTo(out, COPY_BUFFER) }
                    out.flush()
                    runCatching { Os.fsync(pfd.fileDescriptor) }
                    Os.ftruncate(pfd.fileDescriptor, length)
                    runCatching { Os.fsync(pfd.fileDescriptor) }
                }
            } ?: error("openFileDescriptor returned null")
        }
        if (replaced.isSuccess) return
        resolver.openOutputStream(uri, "wt")?.use { out ->
            FileInputStream(tagged).use { input -> input.copyTo(out, COPY_BUFFER) }
            out.flush()
        } ?: throw replaced.exceptionOrNull() ?: error("Unable to replace $uri")
    }

    private fun notifyMediaStore(
        uri: Uri,
        tagged: File,
        fields: AudioTagFields,
        track: LibraryTrackEntity,
    ) {
        if (track.source == LibrarySource.MediaStore.id) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, fields.title)
                put(MediaStore.Audio.Media.ARTIST, fields.artist)
                put(MediaStore.Audio.Media.ALBUM, fields.album)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    put(MediaStore.Audio.Media.ALBUM_ARTIST, fields.albumArtist)
                }
                fields.year?.let { put(MediaStore.Audio.Media.YEAR, it) }
                val packedTrack = ((fields.discNumber ?: 0) * 1000) + (fields.trackNumber ?: 0)
                if (packedTrack > 0) put(MediaStore.Audio.Media.TRACK, packedTrack)
                put(MediaStore.MediaColumns.SIZE, tagged.length())
                put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
            }
            runCatching { resolver.update(uri, values, null, null) }
        }
        resolver.notifyChange(uri, null)
        val path = queryPath(uri)
        if (path != null) {
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(path),
                arrayOf(track.mimeType ?: "audio/*"),
                null,
            )
        }
    }

    private fun querySize(uri: Uri): Long? =
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index).takeIf { it > 0L } else null
        }

    private fun queryPath(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.path
        @Suppress("DEPRECATION")
        return resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index)?.takeIf { it.isNotBlank() } else null
        }
    }

    private fun hasEnoughCacheSpace(sizeBytes: Long): Boolean {
        val usable = appContext.cacheDir.usableSpace
        val needed = (sizeBytes.coerceAtLeast(1L) * 2L) + MIN_FREE_CACHE_BYTES
        return usable > needed
    }

    private fun looksLikeMp3(bytes: ByteArray, n: Int, mimeType: String?): Boolean {
        if (n >= 2) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            if (first == 0xFF && (second and 0xE0) == 0xE0) return true
        }
        val mime = mimeType.orEmpty().lowercase()
        return mime.contains("mpeg") || mime.contains("mp3")
    }

    private fun looksLikeUnsupportedContainer(bytes: ByteArray, n: Int): Boolean {
        if (n >= 8 &&
            bytes[4] == 'f'.code.toByte() &&
            bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() &&
            bytes[7] == 'p'.code.toByte()
        ) {
            return true
        }
        if (n >= 4 &&
            bytes[0] == 'O'.code.toByte() &&
            bytes[1] == 'g'.code.toByte() &&
            bytes[2] == 'g'.code.toByte() &&
            bytes[3] == 'S'.code.toByte()
        ) {
            return true
        }
        return false
    }

    private fun looksLikeRiff(bytes: ByteArray, n: Int): Boolean =
        n >= 4 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()

    private enum class PeekFormat {
        Supported,
        Unsupported,
        Unreadable,
    }

    private companion object {
        const val TAG = "EmbeddedTagWriter"
        const val TAG_CACHE_DIR = "echo-tag-write"
        const val COPY_BUFFER = 64 * 1024
        const val MAX_REWRITE_BYTES = 1L * 1024 * 1024 * 1024
        const val MIN_FREE_CACHE_BYTES = 8L * 1024 * 1024
    }
}
