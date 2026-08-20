package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFingerprintPolicyTest {
    @Test
    fun remoteFingerprintIgnoresWallClock() {
        val first = LibraryFingerprintPolicy.fingerprint(
            contentUri = "https://navidrome.example/stream?id=1",
            sizeBytes = 4_096L,
            sampleRateHz = 44_100,
            dateModifiedSeconds = LibraryFingerprintPolicy.remoteDateModifiedSeconds(1_700_000_000_000L),
            title = "Song",
            artist = "Artist",
            album = "Album",
            albumArtist = "Artist",
            artworkUri = "https://cover",
            durationMs = 180_000L,
            trackNumber = 1,
            discNumber = 1,
            year = 2020,
            mimeType = "audio/flac",
            relativePath = "Music/Album",
            remote = true,
        )
        val second = LibraryFingerprintPolicy.fingerprint(
            contentUri = "https://navidrome.example/stream?id=1",
            sizeBytes = 4_096L,
            sampleRateHz = 44_100,
            dateModifiedSeconds = LibraryFingerprintPolicy.remoteDateModifiedSeconds(1_800_000_000_000L),
            title = "Song",
            artist = "Artist",
            album = "Album",
            albumArtist = "Artist",
            artworkUri = "https://cover",
            durationMs = 180_000L,
            trackNumber = 1,
            discNumber = 1,
            year = 2020,
            mimeType = "audio/flac",
            relativePath = "Music/Album",
            remote = true,
        )
        assertEquals(first, second)
        assertEquals(0L, LibraryFingerprintPolicy.remoteDateModifiedSeconds(9_999_999_999L))
    }

    @Test
    fun remoteEntityFingerprintIgnoresStoredWallClock() {
        val first = LibraryTrackEntity(
            id = "subsonic:demo:song:1",
            contentUri = "https://navidrome.example/stream?id=1",
            title = "Song",
            artist = "Artist",
            album = "Album",
            albumArtist = "Artist",
            artworkUri = "https://cover",
            durationMs = 180_000L,
            trackNumber = 1,
            discNumber = 1,
            year = 2020,
            mimeType = "audio/flac",
            sizeBytes = 4_096L,
            dateModifiedSeconds = LibraryFingerprintPolicy.remoteDateModifiedSeconds(1_700_000_000_000L),
            source = "subsonic:demo",
            relativePath = "Music/Album",
        )
        val second = first.copy(
            dateModifiedSeconds = LibraryFingerprintPolicy.remoteDateModifiedSeconds(1_800_000_000_000L),
        )
        assertEquals(buildTrackFingerprint(first), buildTrackFingerprint(second))
    }
}
