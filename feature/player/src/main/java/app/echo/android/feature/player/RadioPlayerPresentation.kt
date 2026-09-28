package app.echo.android.feature.player

import app.echo.android.model.playback.EchoTrackRef
import java.net.URI

internal data class RadioPlayerPresentation(
    val station: String,
    val host: String,
    val liveTitle: String?,
)

/** ICY song updates use title = song, artist = station; ordinary radio uses artist = host. */
internal fun radioPlayerPresentation(track: EchoTrackRef?): RadioPlayerPresentation {
    if (track == null) return RadioPlayerPresentation("", "", null)
    val host = runCatching { URI(track.uri).host.orEmpty() }.getOrDefault("")
    val artist = track.artist.trim()
    val hasLiveTitle = host.isNotBlank() && artist.isNotBlank() && !artist.equals(host, ignoreCase = true)
    return RadioPlayerPresentation(
        station = if (hasLiveTitle) artist else track.title,
        host = host,
        liveTitle = track.title.takeIf { hasLiveTitle && it.isNotBlank() && it != artist },
    )
}
