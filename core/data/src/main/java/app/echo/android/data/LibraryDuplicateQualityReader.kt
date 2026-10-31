package app.echo.android.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.net.toUri
import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackAudioQuality
import kotlinx.coroutines.CancellationException

/** Called sequentially on the analysis worker. Does not decode PCM or read whole audio files. */
internal class LibraryDuplicateQualityReader(private val context: Context) {
    fun read(track: EchoTrack): EchoTrackAudioQuality? {
        val uri = CueSheetPolicy.playbackUri(track.uri).toUri()
        return try {
            LocalAudioFileAccess.open(context, uri, null).use { descriptor ->
                var rate = track.sampleRateHz
                var depth: Int? = null
                var bitrate: Int? = null
                var codec: String? = null
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(descriptor.fileDescriptor)
                    bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.takeIf { it > 0 }
                    if (Build.VERSION.SDK_INT >= 31) {
                        rate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.takeIf { it > 0 } ?: rate
                        depth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?.takeIf { it in 1..64 }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Codec support may be narrower than ECHO's native decoder. */ }
                finally { retriever.release() }
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(descriptor.fileDescriptor)
                    for (index in 0 until extractor.trackCount.coerceAtMost(16)) {
                        val format = extractor.getTrackFormat(index)
                        val type = format.getString(MediaFormat.KEY_MIME)
                        if (type?.startsWith("audio/") == true) { codec = type; break }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Preserve the known scanned format when extraction is unsupported. */ }
                finally { extractor.release() }
                EchoTrackAudioQuality(rate, depth, bitrate, codec)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
}
