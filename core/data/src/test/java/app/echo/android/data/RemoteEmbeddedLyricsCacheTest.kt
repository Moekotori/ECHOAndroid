package app.echo.android.data

import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.EchoTrack
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RemoteEmbeddedLyricsCacheTest {
    @Test fun remoteCueUsesTheSameSourceCacheAndRejectsStaleMetadata() {
        val root = Files.createTempDirectory("echo-remote-lyrics-").toFile()
        try {
            val source = EchoTrack("remote:album", "https://example.test/album.flac", "Album", "Artist").toLibraryTrackEntity()
                .copy(source = "smb:source", sizeBytes = 1234, dateModifiedSeconds = 42)
            val text = "[01:01.00]Cached lyrics"
            EmbeddedLyricsStore(remoteEmbeddedLyricsDirectory(root)).store(source, text)
            assertEquals(text, readCachedRemoteEmbeddedLyrics(root, source))
            val cue = source.copy(id = CueSheetPolicy.cueTrackId(source.id, 2), contentUri = CueSheetPolicy.contentUri(source.contentUri, 2), clipStartMs = 60_000)
            assertEquals(text, readCachedRemoteEmbeddedLyrics(root, cue))
            assertEquals(text, readCachedRemoteEmbeddedLyrics(root, source.copy(source = "webdav:source")))
            assertNull(readCachedRemoteEmbeddedLyrics(root, cue.copy(dateModifiedSeconds = 43)))
            assertNull(readCachedRemoteEmbeddedLyrics(root, source.copy(source = "mediastore")))
        } finally { root.deleteRecursively() }
    }
}
