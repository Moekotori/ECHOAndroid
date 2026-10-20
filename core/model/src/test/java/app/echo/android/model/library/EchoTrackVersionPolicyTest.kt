package app.echo.android.model.library

import org.junit.Assert.*
import org.junit.Test

class EchoTrackVersionPolicyTest {
    private val remote = EchoTrack("remote", "https://example.test/song", "Song", "Artist", "Album", durationMs = 180000, source = LibrarySource.Subsonic)
    private val local = remote.copy(id = "local", uri = "content://audio/1", source = LibrarySource.MediaStore)

    @Test fun repeatedWhitespaceIsNormalized() {
        assertTrue(EchoTrackVersionPolicy.matches(remote.copy(title = "Song  Name"), local.copy(title = "Song\tName")))
    }

    @Test fun editionAndDurationMustAgree() {
        assertTrue(EchoTrackVersionPolicy.matches(remote, local.copy(durationMs = 181000)))
        assertFalse(EchoTrackVersionPolicy.matches(remote, local.copy(title = "Song (Live)")))
        assertFalse(EchoTrackVersionPolicy.matches(remote, local.copy(album = "Album (Remastered)")))
        assertFalse(EchoTrackVersionPolicy.matches(remote, local.copy(durationMs = 190000)))
        assertFalse(EchoTrackVersionPolicy.matches(remote, local.copy(artist = "Other")))
        assertFalse(EchoTrackVersionPolicy.matches(remote, local.copy(clipStartMs = 30000, clipEndMs = 210000)))
        assertFalse(EchoTrackVersionPolicy.matches(remote.copy(album = null), local.copy(album = null)))
    }

    @Test fun explicitPreferenceWinsAndUnavailableVersionFallsBack() {
        val candidates = listOf(remote, local)
        assertEquals("remote", EchoTrackVersionPolicy.choose(remote, candidates, "remote", setOf("remote", "local")).id)
        assertEquals("local", EchoTrackVersionPolicy.choose(remote, candidates, "remote", setOf("local")).id)
        assertEquals("local", EchoTrackVersionPolicy.choose(remote, candidates, null, setOf("remote", "local")).id)
        val otherRemote = remote.copy(id = "other")
        assertEquals(otherRemote, EchoTrackVersionPolicy.choose(remote, listOf(remote, otherRemote), null, setOf("other")))
        assertEquals(remote, EchoTrackVersionPolicy.choose(remote, candidates, null, emptySet()))
    }
}
