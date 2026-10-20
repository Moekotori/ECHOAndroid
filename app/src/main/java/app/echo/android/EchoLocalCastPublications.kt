package app.echo.android

import app.echo.android.connect.EchoLinkCastFormat
import app.echo.android.connect.EchoLinkCastPolicy
import app.echo.android.connect.EchoLinkCastPublication
import app.echo.android.connect.EchoLinkCastSourceTrack

internal fun localCastPublications(
    tracks: List<EchoLinkCastSourceTrack>,
    peerHost: String? = null,
    checkActive: () -> Unit = {},
): List<EchoLinkCastPublication> {
    return tracks.map { track ->
        checkActive()
        val artworkToken = track.artworkUri
            ?.takeIf { EchoLinkCastPolicy.isLocalFileUri(it) }
            ?.let { EchoLinkCastPolicy.newToken() }
        EchoLinkCastPublication(
            token = EchoLinkCastPolicy.newToken(),
            trackId = track.id,
            peerHost = peerHost,
            uri = app.echo.android.model.library.CueSheetPolicy.playbackUri(track.uri),
            sourceRange = if (track.clipStartMs > 0 || track.clipEndMs > 0) app.echo.android.model.connect.EchoRemoteSourceRange(
                track.clipStartMs / 1000.0, track.clipEndMs.takeIf { it > track.clipStartMs }?.div(1000.0)) else null,
            mimeType = track.mimeType,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.artworkUri,
            artworkToken = artworkToken,
            durationMs = track.durationMs,
            format = EchoLinkCastFormat.fromTrack(
                uri = app.echo.android.model.library.CueSheetPolicy.playbackUri(track.uri),
                mimeType = track.mimeType,
                sampleRateHz = track.sampleRateHz,
                bitDepth = track.bitDepth,
                channelCount = track.channelCount,
                codec = track.codec,
            ),
        )
    }
}
