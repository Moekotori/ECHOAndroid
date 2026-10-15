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
        require(track.clipStartMs == 0L && track.clipEndMs == 0L) { "Clipped tracks cannot be published" }
        val artworkToken = track.artworkUri
            ?.takeIf { EchoLinkCastPolicy.isLocalFileUri(it) }
            ?.let { EchoLinkCastPolicy.newToken() }
        EchoLinkCastPublication(
            token = EchoLinkCastPolicy.newToken(),
            trackId = track.id,
            peerHost = peerHost,
            uri = track.uri,
            mimeType = track.mimeType,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.artworkUri,
            artworkToken = artworkToken,
            durationMs = track.durationMs,
            format = EchoLinkCastFormat.fromTrack(
                uri = track.uri,
                mimeType = track.mimeType,
                sampleRateHz = track.sampleRateHz,
                bitDepth = track.bitDepth,
                channelCount = track.channelCount,
                codec = track.codec,
            ),
        )
    }
}
