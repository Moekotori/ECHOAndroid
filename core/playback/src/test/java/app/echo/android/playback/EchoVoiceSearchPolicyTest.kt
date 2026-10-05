package app.echo.android.playback

import app.echo.android.model.playback.EchoMediaSearchRequest
import org.junit.Assert.*
import org.junit.Test

class EchoVoiceSearchPolicyTest {
    private fun item(id: String, title: String, kind: EchoPlaybackBrowseKind, artist: String = "") =
        EchoPlaybackBrowseItem(id, title, subtitle = artist, browsable = kind != EchoPlaybackBrowseKind.Track, playable = true, kind = kind)

    @Test fun albumFocusWinsOverIncidentalSongHints() {
        val song = item("song", "Blue", EchoPlaybackBrowseKind.Track, "Artist")
        val album = item("album", "Blue", EchoPlaybackBrowseKind.Album, "Artist")
        assertEquals(album, EchoVoiceSearchPolicy.select(EchoMediaSearchRequest("Blue", focus = "vnd.android.cursor.item/album",
            title = "Other song", album = "Blue", artist = "Artist"), listOf(song, album)))
    }

    @Test fun songArtistDisambiguatesAndMissingSongNeverFallsBackToArtist() {
        val first = item("a", "Blue", EchoPlaybackBrowseKind.Track, "Other")
        val second = item("b", "Blue", EchoPlaybackBrowseKind.Track, "Artist")
        val artist = item("c", "Blue", EchoPlaybackBrowseKind.Artist)
        assertEquals(second, EchoVoiceSearchPolicy.select(EchoMediaSearchRequest("Blue", title = "Blue", artist = "Artist"), listOf(first, second, artist)))
        assertNull(EchoVoiceSearchPolicy.select(EchoMediaSearchRequest("Missing", title = "Missing"), listOf(artist)))
    }

    @Test fun playlistFocusAndExactMatchBuildOneCollectionRatherThanSongResults() {
        val exact = item("p", "Commute", EchoPlaybackBrowseKind.Playlist)
        val partial = item("a", "Commute evening", EchoPlaybackBrowseKind.Playlist)
        val song = item("s", "Commute", EchoPlaybackBrowseKind.Track)
        assertEquals(exact, EchoVoiceSearchPolicy.select(EchoMediaSearchRequest("Commute", playlist = "Commute"), listOf(song, partial, exact)))
    }

    @Test fun queueAnchorPreservesRequestedSongPosition() {
        assertEquals(2, PlaybackCatalogPolicy.resolvedStartIndex(listOf("third"), listOf("first", "second", "third"), 0))
        assertTrue(EchoMediaSearchRequest("   ").term.isEmpty())
    }
}
