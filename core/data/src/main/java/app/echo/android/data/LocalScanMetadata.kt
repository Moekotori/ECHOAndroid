package app.echo.android.data

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Tag readers may tolerate a failed optional read; scanning must still schedule a retry. */
internal fun <T> readScanMetadata(input: InputStream, reader: (InputStream) -> T): T {
    var failure: IOException? = null
    val checked = object : FilterInputStream(input), TagTailReader {
        private fun <R> checkRead(block: () -> R): R = try {
            block()
        } catch (error: IOException) {
            failure = error
            throw error
        }

        override fun read(): Int = checkRead { `in`.read() }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
            checkRead { `in`.read(bytes, offset, length) }
        override fun skip(count: Long): Long = checkRead { `in`.skip(count) }
        override fun tail(count: Int): ByteArray? = checkRead { readTagTail(input, count) }
    }
    return checked.use {
        val result = reader(it)
        failure?.let { error -> throw error }
        result
    }
}

internal fun retainFailedScanMetadata(
    incoming: LibraryTrackEntity,
    existing: LibraryTrackEntity?,
): LibraryTrackEntity {
    if (incoming.fingerprint != LibraryScanPolicy.PendingDocumentMetadataFingerprint || existing == null) return incoming
    return existing.copy(
        contentUri = incoming.contentUri,
        relativePath = incoming.relativePath,
        fileName = incoming.fileName ?: existing.fileName,
        sizeBytes = incoming.sizeBytes,
        dateModifiedSeconds = incoming.dateModifiedSeconds,
        fingerprint = LibraryScanPolicy.PendingDocumentMetadataFingerprint,
    )
}
