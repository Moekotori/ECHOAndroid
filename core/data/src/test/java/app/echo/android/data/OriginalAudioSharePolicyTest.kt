package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import org.junit.Assert.*
import org.junit.Test

class OriginalAudioSharePolicyTest {
    private fun track(uri: String, source: LibrarySource = LibrarySource.MediaStore, id: String = "song") =
        EchoTrack(id, uri, "Song", "Artist", source = source)

    @Test fun onlyLocalOriginalsAreAcceptedIncludingUnindexedIncomingFiles() {
        assertTrue(OriginalAudioSharePolicy.supports(track("content://media/external/audio/media/1")))
        assertTrue(OriginalAudioSharePolicy.supports(track("file:///music/a.flac", LibrarySource.Saf)))
        assertTrue(OriginalAudioSharePolicy.supports(track("content://files/song", LibrarySource.Unknown, "incoming:song")))
        assertFalse(OriginalAudioSharePolicy.supports(track("https://server/song", LibrarySource.Saf)))
        assertFalse(OriginalAudioSharePolicy.supports(track("content://files/song", LibrarySource.WebDav)))
        assertFalse(OriginalAudioSharePolicy.supports(track("content://files/song", LibrarySource.Unknown)))
    }

    @Test fun cueTracksResolveToTheSameCompleteOriginal() {
        val first = track("content://media/audio/7#echo-cue=1", id = "song#cue:1")
        val second = track("content://media/audio/7#echo-cue=2", id = "song#cue:2")
        assertEquals("content://media/audio/7", OriginalAudioSharePolicy.originalUri(first))
        assertEquals(OriginalAudioSharePolicy.originalUri(first), OriginalAudioSharePolicy.originalUri(second))
    }

    @Test fun filenamesCannotEscapeTheSessionAndCollisionsKeepTheirExtensions() {
        val names = hashSetOf("manifest.json")
        assertEquals("song.flac", OriginalAudioSharePolicy.uniqueName("../../song.flac", names))
        assertEquals("SONG (2).flac", OriginalAudioSharePolicy.uniqueName("SONG.flac", names))
        assertEquals("manifest (2).json", OriginalAudioSharePolicy.uniqueName("manifest.json", names))
        assertEquals("cover.png", OriginalAudioSharePolicy.uniqueName("C:\\music\\cover.png", names))
        assertFalse(OriginalAudioSharePolicy.safeName("../\\\u0000bad:name").contains(':'))
        val long = OriginalAudioSharePolicy.safeName("a".repeat(200) + ".flac")
        assertEquals(160, long.length)
        assertTrue(long.endsWith(".flac"))
        val unicode = OriginalAudioSharePolicy.safeName("曲".repeat(200) + ".flac")
        assertTrue(unicode.toByteArray(Charsets.UTF_8).size <= 200)
        assertTrue(unicode.endsWith(".flac"))
    }

    @Test fun differentFormatsAndAttachmentsKeepDistinctMatchingStems() {
        val names = OriginalAudioShareNames()
        assertEquals("Song.flac", names.audio("Song.flac"))
        assertEquals("Song.lrc", names.attachment("Song.lrc"))
        assertEquals("Song.jpg", names.attachment("Song.jpg"))
        assertEquals("song (2).mp3", names.audio("song.mp3"))
        assertEquals("song (2).lrc", names.attachment("song (2).lrc"))
        assertEquals("song (2).jpg", names.attachment("song (2).jpg"))
        assertEquals("manifest (2).json", names.audio("manifest.json"))
    }
}
