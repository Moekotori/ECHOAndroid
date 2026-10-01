package app.echo.android.widget

import app.echo.android.playback.EchoPlaybackSurfaceSnapshot

/** Responsive home/lock-screen sizes in dp. All button targets are at least 44 dp. */
object EchoPlaybackWidgetLayout {
    const val CompactWidthDp = 180
    const val CompactHeightDp = 56
    const val ExpandedWidthDp = 250
    const val ExpandedHeightDp = 110
    const val WideWidthDp = 300
    const val WideHeightDp = 72
    const val TallWidthDp = 250
    const val TallHeightDp = 180

    fun mode(widthDp: Int, heightDp: Int): EchoPlaybackWidgetMode = when {
        widthDp >= TallWidthDp && heightDp >= TallHeightDp -> EchoPlaybackWidgetMode.Tall
        isExpanded(widthDp, heightDp) -> EchoPlaybackWidgetMode.Expanded
        widthDp >= WideWidthDp && heightDp >= WideHeightDp -> EchoPlaybackWidgetMode.Wide
        else -> EchoPlaybackWidgetMode.Compact
    }

    fun isExpanded(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= ExpandedWidthDp && heightDp >= ExpandedHeightDp

    fun chrome(snapshot: EchoPlaybackSurfaceSnapshot, lyricLine: String?): EchoPlaybackWidgetChrome =
        EchoPlaybackWidgetChrome(
            title = snapshot.title,
            artist = snapshot.artist,
            isPlaying = snapshot.isPlaying,
            hasTrack = snapshot.hasTrack,
            mediaId = snapshot.mediaId,
            artworkUri = snapshot.artworkUri,
            playUri = snapshot.playUri,
            lyricLine = lyricLine,
        )
}

enum class EchoPlaybackWidgetMode { Compact, Wide, Expanded, Tall }

data class EchoPlaybackWidgetChrome(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val hasTrack: Boolean,
    val mediaId: String?,
    val artworkUri: String?,
    val playUri: String?,
    val lyricLine: String?,
)
