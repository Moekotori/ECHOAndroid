package app.echo.android.data

import app.echo.android.model.library.AlbumSummary
import org.junit.Assert.*
import org.junit.Test

class AlbumOnlineQueryPolicyTest {
    private val album = AlbumSummary("a", "Blue (Deluxe Edition)", "Artist A / Artist B", null, null, 10, 0, null)

    @Test fun collaboratorSearchesAreBoundedAndTryTheCanonicalFullCreditFirst() {
        val queries = AlbumOnlineQueryPolicy.musicBrainzQueries(album)
        assertEquals(4, queries.size)
        assertTrue(queries.first().contains("artist:\"Artist A & Artist B\""))
        assertTrue(queries[1].contains("release:\"Blue\""))
        assertEquals(listOf("AC/DC"), AlbumOnlineQueryPolicy.artistParts("AC/DC"))
    }

    @Test fun unknownAlbumArtistFallsBackToTheExistingTrackArtist() {
        assertEquals("Björk", AlbumOnlineQueryPolicy.artist(album.copy(albumArtist = "Unknown Artist", artist = "Björk")))
        assertNull(AlbumOnlineQueryPolicy.artist(album.copy(albumArtist = "Unknown Artist", artist = "Unknown")))
    }

    @Test fun kanaMetadataPrefersJapaneseWhileKeepingTheInterfaceLanguageAsFallback() {
        assertEquals(listOf("ja", "zh", "en"), AlbumOnlineQueryPolicy.wikipediaLanguages("zh", "視界の隅 朽ちる音", "トゲナシトゲアリ"))
        assertEquals("zh", AlbumOnlineQueryPolicy.wikipediaLanguages("zh", "Meddle", "Pink Floyd").first())
    }

    @Test fun encyclopediaSearchCanRemoveNestedPerformerAnnotationsWithoutMergingLiveWorks() {
        assertEquals("視界の隅 朽ちる音", AlbumOnlineQueryPolicy.wikipediaTitles("視界の隅 朽ちる音 (新川崎(仮))").first())
        assertEquals(listOf("Blue (Live)"), AlbumOnlineQueryPolicy.wikipediaTitles("Blue (Live)"))
        assertEquals(listOf("Blue (Remix)"), AlbumOnlineQueryPolicy.wikipediaTitles("Blue (Remix)"))
    }
}
