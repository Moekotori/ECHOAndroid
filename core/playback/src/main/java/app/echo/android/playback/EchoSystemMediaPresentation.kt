package app.echo.android.playback

import android.os.Bundle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants

/** Shared by system media controls, OEM music islands and legacy media descriptions. */
internal object EchoSystemMediaPresentation {
    fun notificationText(
        artist: CharSequence?,
        lyric: String?,
        lyricsEnabled: Boolean,
        currentMediaId: String?,
        lyricMediaId: String?,
    ): CharSequence? = lyric?.takeIf {
        lyricsEnabled && !currentMediaId.isNullOrBlank() && currentMediaId == lyricMediaId && it.isNotBlank()
    } ?: artist
}

/** Keep canonical tags intact; systems reading DISPLAY_* receive the same text as our notification. */
internal fun MediaMetadata.withSystemMediaDescription(
    radio: EchoRadioDisplay? = radioNowPlayingOrNull(),
): MediaMetadata {
    return buildUpon()
        .setDisplayTitle(radio?.title ?: title)
        // When DISPLAY_TITLE is present, legacy descriptions no longer infer the artist.
        .setSubtitle(radio?.artist ?: artist)
        .setDescription(albumTitle)
        .build()
}

@UnstableApi
internal fun echoSystemMediaSessionExtras(): Bundle = Bundle().apply {
    // Prevent custom actions from taking the previous/next slots at queue boundaries.
    putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, true)
    putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, true)
}
