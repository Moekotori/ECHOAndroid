package app.echo.android.data

import android.content.ContentResolver
import android.net.Uri
import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetPolicy
import java.io.IOException
import kotlinx.coroutines.CancellationException

internal sealed interface CueExpansion {
    data class Success(val tracks: List<LibraryTrackEntity>) : CueExpansion
    data object ReadFailed : CueExpansion
}

/** Failure is not evidence that a CUE disappeared: scanners retain old IDs without writing rows. */
internal fun LibraryTrackEntity.resolveLocalCue(
    external: CueSheet?,
    audioName: String?,
    externalReadFailed: Boolean = false,
    readEmbedded: () -> CueSheet?,
): CueExpansion {
    if (external != null) return CueExpansion.Success(splitByCue(external, audioName))
    if (externalReadFailed) return CueExpansion.ReadFailed
    return try {
        val embedded = readEmbedded()
        CueExpansion.Success(embedded?.let { splitByCue(it) }
            ?: listOf(copy(fingerprint = CueScanState.clearWholeFile(fingerprint))))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        CueExpansion.ReadFailed
    }
}

/** Background scanners only; the playback path never discovers CUEs. */
internal fun LibraryTrackEntity.expandLocalCue(
    resolver: ContentResolver,
    external: CueSheet?,
    audioName: String?,
    externalReadFailed: Boolean = false,
): CueExpansion = resolveLocalCue(external, audioName, externalReadFailed) {
    val isFlac = audioName?.endsWith(".flac", true) == true || mimeType in setOf("audio/flac", "audio/x-flac")
    if (!isFlac) null else {
        val input = resolver.openInputStream(Uri.parse(CueSheetPolicy.playbackUri(contentUri)))
            ?: throw IOException("Cannot open FLAC CUE metadata")
        input.use(FlacEmbeddedCueReader::readChecked)
    }
}
