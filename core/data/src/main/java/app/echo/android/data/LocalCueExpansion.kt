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
    fun expand(sheet: CueSheet, name: String?): CueExpansion {
        val movements = if (name == null) sheet.tracks else CueSheetPolicy.tracksForAudio(sheet, name)
        // Invalid timing is a read/validation failure, never proof that old movements disappeared.
        if (movements.isEmpty() || CueSheetPolicy.withEndTimes(movements, durationMs).isEmpty()) return CueExpansion.ReadFailed
        return CueExpansion.Success(splitByCue(sheet, name))
    }
    if (external != null) return expand(external, audioName)
    if (externalReadFailed) return CueExpansion.ReadFailed
    return try {
        val embedded = readEmbedded()
        if (embedded != null) expand(embedded, null)
        else CueExpansion.Success(listOf(copy(fingerprint = CueScanState.clearWholeFile(fingerprint))))
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
    val isFlac = LocalAudioFileTypes.mimeTypeForFileName(audioName.orEmpty()) == "audio/flac" ||
        mimeType?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT) in
        setOf("audio/flac", "audio/x-flac", "application/flac", "application/x-flac")
    val isApeTagContainer = audioName?.substringAfterLast('.')?.lowercase(java.util.Locale.ROOT) in setOf("ape", "wv", "tta", "tak") ||
        mimeType?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT) in
        setOf("audio/ape", "audio/x-ape", "audio/x-monkeys-audio", "audio/x-wavpack", "audio/wavpack", "audio/tta", "audio/x-tta", "audio/tak", "audio/x-tak")
    if (!isFlac && !isApeTagContainer) null else {
        val input = resolver.openInputStream(Uri.parse(CueSheetPolicy.playbackUri(contentUri)))
            ?: throw IOException("Cannot open embedded CUE metadata")
        input.use { if (isFlac) FlacEmbeddedCueReader.readChecked(it) else ApeTagReader.readCueChecked(it) }
    }
}
