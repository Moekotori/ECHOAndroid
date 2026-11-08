package app.echo.android.playback

import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoSystemMediaPresentationTest {
    @Test
    fun explicitDisplayTitleKeepsArtistAndAlbumInLegacyDescriptions() {
        val metadata = MediaMetadata.Builder()
            .setTitle("Song")
            .setArtist("Artist")
            .setAlbumTitle("Album")
            .setDurationMs(180_000)
            .build().withSystemMediaDescription()

        assertEquals("Song", metadata.displayTitle)
        assertEquals("Artist", metadata.subtitle)
        assertEquals("Album", metadata.description)
        assertEquals("Song", metadata.title)
        assertEquals(180_000L, metadata.durationMs)
    }

    @Test
    fun missingArtistDoesNotReuseAnOldSubtitle() {
        val metadata = MediaMetadata.Builder()
            .setTitle("Instrumental")
            .setSubtitle("Stale artist")
            .build().withSystemMediaDescription()
        assertNull(metadata.subtitle)
    }

    @Test
    fun radioPublishesCurrentSongWithoutReplacingStationIdentity() {
        val metadata = MediaMetadata.Builder()
            .setTitle("Station")
            .setArtist("radio.example")
            .build().withSystemMediaDescription(EchoRadioDisplay("Artist - Song", "Station"))
        assertEquals("Artist - Song", metadata.displayTitle)
        assertEquals("Station", metadata.subtitle)
        assertEquals("Station", metadata.title)
        assertEquals("radio.example", metadata.artist)
    }

    @Test
    fun switchingTracksCannotPublishThePreviousSongsLyric() {
        assertEquals("Artist", text(current = "new", lyricTrack = "old"))
        assertEquals("Lyric", text(current = "new", lyricTrack = "new"))
        assertEquals("Artist", text(current = null, lyricTrack = null))
    }

    @Test
    fun disabledOrEmptyLyricsKeepArtistVisible() {
        assertEquals("Artist", text(enabled = false))
        assertEquals("Artist", text(lyric = " \n "))
        assertEquals("Artist", text(lyric = null))
    }

    private fun text(
        current: String? = "track",
        lyricTrack: String? = "track",
        lyric: String? = "Lyric",
        enabled: Boolean = true,
    ) = EchoSystemMediaPresentation.notificationText("Artist", lyric, enabled, current, lyricTrack)
}
