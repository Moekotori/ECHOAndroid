package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFtsSearchTest {
    @Test
    fun blankQueryReturnsNull() {
        assertNull(sanitizeFtsQuery("   "))
    }

    @Test
    fun titleQueryUsesPrefixMatch() {
        assertEquals("hotel* AND california*", sanitizeFtsQuery("Hotel California"))
    }

    @Test
    fun artistQueryKeepsLettersAndNumbers() {
        assertEquals("artist* AND 2026*", sanitizeFtsQuery("Artist #2026!"))
    }

    @Test
    fun albumQueryDropsFtsReservedCharacters() {
        assertEquals("echo* AND final*", sanitizeFtsQuery("echo + final : * \""))
    }

    @Test
    fun chineseQueryUsesCharacterTokens() {
        assertEquals(
            "\u9752* AND \u82b1* AND \u74f7*",
            sanitizeFtsQuery("\u9752\u82b1\u74f7"),
        )
    }

    @Test
    fun specialCharactersDoNotCrash() {
        assertNotNull(sanitizeFtsQuery("\"*:+-()[]{} NEAR / \\"))
    }

    @Test
    fun ftsEntityContainsChineseCharacterTokensAndPinyin() {
        val entity = LibraryTrackEntity(
            id = "track-1",
            contentUri = "content://track/1",
            title = "\u9752\u82b1\u74f7",
            artist = "\u5468\u6770\u4f26",
            album = "\u6211\u5f88\u5fd9",
            albumArtist = "\u5468\u6770\u4f26",
            artworkUri = null,
            durationMs = 180_000L,
            trackNumber = 1,
            discNumber = null,
            year = 2007,
            mimeType = "audio/flac",
            sizeBytes = 1024L,
            dateModifiedSeconds = 1L,
        ).withScanMetadata()

        val fts = entity.toFtsEntity()

        assertEquals("track-1", fts.trackId)
        assertTrue(fts.normalizedText.contains("\u9752\u82b1\u74f7"))
        assertTrue(fts.normalizedText.contains("\u9752 \u82b1 \u74f7"))
        assertTrue(fts.normalizedText.contains("\u5468 \u6770 \u4f26"))
        assertTrue(fts.normalizedText.contains("qinghuaci"))
        assertTrue(fts.normalizedText.contains("qhc"))
    }

    @Test
    fun pinyinStoresInitialsForChineseMatching() {
        val entity = LibraryTrackEntity(
            id = "track-2",
            contentUri = "content://track/2",
            title = "\u7d2b\u8587",
            artist = "\u743c\u7476",
            album = null,
            albumArtist = null,
            artworkUri = null,
            durationMs = 180_000L,
            trackNumber = 1,
            discNumber = null,
            year = 1998,
            mimeType = "audio/flac",
            sizeBytes = 1024L,
            dateModifiedSeconds = 1L,
        ).withScanMetadata()

        assertEquals("ziwei zw", entity.pinyinTitle)
        assertTrue(entity.toFtsEntity().normalizedText.contains("ziwei"))
        assertTrue(entity.toFtsEntity().normalizedText.contains("zw"))
    }
}
