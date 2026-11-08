package app.echo.android.data

import android.content.ContentResolver
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.annotation.WorkerThread
import kotlinx.coroutines.CancellationException

/** Reads format headers only. Call on an I/O worker, never from playback's PCM path. */
@WorkerThread
fun readLocalAudioSampleRate(
    resolver: ContentResolver,
    uri: Uri,
    retriever: MediaMetadataRetriever? = null,
): Int? {
    if (uri.scheme !in setOf("content", "file")) return null
    return sampleRateWithFallback(
        metadata = {
            if (Build.VERSION.SDK_INT >= 31 && retriever != null) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            } else null
        },
        format = {
            val extractor = MediaExtractor()
            try {
                resolver.openAssetFileDescriptor(uri, "r")?.use { asset ->
                    // SAF providers may expose just a slice of a larger file.
                    if (asset.declaredLength < 0) extractor.setDataSource(asset.fileDescriptor)
                    else extractor.setDataSource(asset.fileDescriptor, asset.startOffset, asset.declaredLength)
                    var rate: Int? = null
                    for (index in 0 until extractor.trackCount.coerceAtMost(16)) {
                        val track = extractor.getTrackFormat(index)
                        if (track.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true &&
                            track.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            rate = track.getInteger(MediaFormat.KEY_SAMPLE_RATE).takeIf { it > 0 }
                            if (rate != null) break
                        }
                    }
                    rate
                }
            } finally { extractor.release() }
        },
    )
}

/** Failed/empty retriever metadata must not prevent the older extractor path. */
internal fun sampleRateWithFallback(metadata: () -> Int?, format: () -> Int?): Int? {
    fun read(block: () -> Int?): Int? = try {
        block()?.takeIf { it > 0 }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }
    return read(metadata) ?: read(format)
}
