package app.echo.android

import app.echo.android.connect.EchoLinkCastSourceTrack
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackQueueState

/** Queue identity also protects duplicate occurrences and reorderings during preparation. */
internal data class EchoPhoneCastSource(
    val queue: PlaybackQueueState,
    val playback: EchoPlaybackStatus,
    val positionMs: Long,
) {
    fun isSameSelection(other: EchoPhoneCastSource): Boolean =
        queue === other.queue && playback.track?.id == other.playback.track?.id &&
            playback.track?.uri == other.playback.track?.uri &&
            playback.track?.queueContext?.entryId == other.playback.track?.queueContext?.entryId
}

internal fun EchoTrackRef.toCastSource(
    diagnostics: EchoPlaybackDiagnostics? = null,
): EchoLinkCastSourceTrack = EchoLinkCastSourceTrack(
    id = id,
    uri = uri,
    title = title,
    artist = artist,
    album = album,
    artworkUri = artworkUri,
    durationMs = durationMs,
    sourceId = sourceId,
    clipStartMs = clipStartMs,
    clipEndMs = clipEndMs,
    sampleRateHz = diagnostics?.sampleRateHz?.takeIf { it > 0 } ?: sampleRateHz,
    bitDepth = diagnostics?.bitDepth?.takeIf { it > 0 },
    channelCount = diagnostics?.channelCount?.takeIf { it > 0 },
    codec = diagnostics?.codec?.takeIf { it.isNotBlank() },
)

