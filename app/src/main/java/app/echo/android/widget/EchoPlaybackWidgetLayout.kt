package app.echo.android.widget

import app.echo.android.playback.EchoPlaybackSurfaceSnapshot

/** Glance SizeMode.Responsive sizes, in dp. Compact is 3×1; expanded is 4×2. */
object EchoPlaybackWidgetLayout {
    const val CompactWidthDp = 180
    const val CompactHeightDp = 56
    const val ExpandedWidthDp = 250
    const val ExpandedHeightDp = 110

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
