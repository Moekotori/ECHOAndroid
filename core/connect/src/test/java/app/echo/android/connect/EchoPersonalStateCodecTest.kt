package app.echo.android.connect

import app.echo.android.model.connect.EchoPersonalBookmark
import app.echo.android.model.connect.EchoPersonalListen
import app.echo.android.model.connect.EchoPersonalLyricsOffset
import app.echo.android.model.connect.EchoPersonalResume
import app.echo.android.model.connect.EchoPersonalState
import app.echo.android.model.connect.EchoPersonalStateLimits
import app.echo.android.model.connect.EchoSyncTrackRef
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoPersonalStateCodecTest {
    private val track = EchoSyncTrackRef("Song", "Artist", null, 180_000L)
    private fun snapshot() = EchoPersonalState(
        history = listOf(EchoPersonalListen(track, 90_000L, 1_700_000_000_000L, 19_675L, 12)),
        bookmarks = listOf(EchoPersonalBookmark(track, 1_000L, "Point"), EchoPersonalBookmark(track, 2_000L, "Range", 3_000L)),
        lyricsOffsets = listOf(EchoPersonalLyricsOffset(track, -500L)),
        resume = EchoPersonalResume(track, 45_000L),
        revision = "reviewed-revision",
    )

    @Test fun roundTripsBoundedMetadataIncludingNullableAlbumAndBookmarkRange() {
        val original = snapshot()
        assertEquals(original, EchoPersonalStateCodec.decode(EchoPersonalStateCodec.encode(original)))
        assertEquals(EchoPersonalState(), EchoPersonalStateCodec.decode(EchoPersonalStateCodec.encode(EchoPersonalState())))
        val legacyPoint = EchoPersonalStateCodec.encode(original)
        legacyPoint.getJSONArray("bookmarks").getJSONObject(0).remove("endPositionMs")
        assertNull(EchoPersonalStateCodec.decode(legacyPoint).bookmarks.first().endPositionMs)
    }

    @Test fun revisionIgnoresResumeAndServerRevisionButTracksPersonalWrites() {
        val state = snapshot()
        val revision = EchoPersonalStateCodec.revision(state)
        assertEquals(revision, EchoPersonalStateCodec.revision(state.copy(
            revision = "another-server-revision", resume = EchoPersonalResume(track, 80_000L),
        )))
        assertNotEquals(revision, EchoPersonalStateCodec.revision(state.copy(
            bookmarks = listOf(state.bookmarks.first().copy(label = "Updated")),
        )))
    }

    @Test fun rejectsUnsupportedVersionAndInvalidTimestampRangeOrOffset() {
        fun rejected(change: (JSONObject) -> Unit) {
            val json = EchoPersonalStateCodec.encode(snapshot())
            change(json)
            assertTrue(runCatching { EchoPersonalStateCodec.decode(json) }.isFailure)
        }
        rejected { it.put("version", 2) }
        rejected { it.getJSONArray("history").getJSONObject(0).put("playedAtEpochMs", -1L) }
        rejected { it.getJSONArray("bookmarks").getJSONObject(1).put("endPositionMs", 1_999L) }
        rejected { it.getJSONArray("lyricsOffsets").getJSONObject(0).put("offsetMs", 30_001L) }
    }

    @Test fun rejectsOversizedCollectionsOnBothSidesOfTheCodec() {
        val excessive = List(EchoPersonalStateLimits.Bookmarks + 1) { EchoPersonalBookmark(track, it.toLong(), "") }
        assertTrue(runCatching { EchoPersonalStateCodec.encode(EchoPersonalState(bookmarks = excessive)) }.isFailure)
        val json = EchoPersonalStateCodec.encode(EchoPersonalState())
        val row = EchoPersonalStateCodec.encode(snapshot()).getJSONArray("bookmarks").getJSONObject(0)
        json.put("bookmarks", JSONArray().apply { repeat(excessive.size) { put(row) } })
        assertTrue(runCatching { EchoPersonalStateCodec.decode(json) }.isFailure)
    }
}
