package app.echo.android.data

import app.echo.android.model.library.ArtistSetlist
import app.echo.android.model.library.ArtistSetlistSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetlistMatchPolicyTest {
    @Test
    fun exactTitleMatchesASingleRow() {
        val matched = SetlistMatchPolicy.match(
            setlist("Computer City"),
            listOf(row("t1", "Computer City", "Perfume")),
        )
        assertEquals("t1", matched.songs.single().trackId)
    }

    @Test
    fun liveSuffixStillMatchesNormalizedTitle() {
        val matched = SetlistMatchPolicy.match(
            setlist("Computer City (Live)"),
            listOf(row("t1", "Computer City", "Perfume")),
        )
        assertEquals("t1", matched.songs.single().trackId)
    }

    @Test
    fun artistNarrowsAmbiguousTitles() {
        val matched = SetlistMatchPolicy.match(
            setlist("Edge"),
            listOf(
                row("other", "Edge", "Other Band"),
                row("hit", "Edge", "Perfume"),
            ),
        )
        assertEquals("hit", matched.songs.single().trackId)
    }

    @Test
    fun ambiguousTitlesWithoutArtistNarrowingAreDropped() {
        val matched = SetlistMatchPolicy.match(
            setlist("Edge"),
            listOf(
                row("a", "Edge", "One"),
                row("b", "Edge", "Two"),
            ),
        )
        assertNull(matched.songs.single().trackId)
    }

    @Test
    fun tapeAndBlankSongsStayUnmatched() {
        val setlist = ArtistSetlist(
            id = "s",
            artistName = "Perfume",
            eventDate = "2026-09-17",
            venue = null,
            city = null,
            url = null,
            songs = listOf(
                ArtistSetlistSong("Intro", tape = true),
                ArtistSetlistSong("   "),
            ),
            exactDate = true,
        )
        val matched = SetlistMatchPolicy.match(
            setlist,
            listOf(row("t1", "Intro", "Perfume")),
        )
        assertNull(matched.songs[0].trackId)
        assertNull(matched.songs[1].trackId)
    }

    @Test
    fun playableIdsKeepSetlistOrderAndDropDuplicates() {
        val ids = SetlistMatchPolicy.playableTrackIds(
            listOf(
                ArtistSetlistSong("A", trackId = "t1"),
                ArtistSetlistSong("B"),
                ArtistSetlistSong("C", trackId = "t1"),
                ArtistSetlistSong("D", trackId = "t2"),
            ),
        )
        assertEquals(listOf("t1", "t2"), ids)
    }

    private fun setlist(title: String) = ArtistSetlist(
        id = "s",
        artistName = "Perfume",
        eventDate = "2026-09-17",
        venue = null,
        city = null,
        url = null,
        songs = listOf(ArtistSetlistSong(title)),
        exactDate = true,
    )

    private fun row(id: String, title: String, artist: String) = SetlistMatchRow(
        id = id,
        title = title,
        artist = artist,
        normalizedTitle = title.normalizedSetlistTitle(),
        normalizedArtist = artist.normalizedSetlistTitle(),
    )
}
