package app.echo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenBrainzClientTest {
    @Test
    fun submitBodyUsesPlayingNowWithoutTimestamp() {
        val body = listenBrainzSubmitBody(
            listenType = "playing_now",
            track = ListenBrainzTrack(
                id = "t1",
                title = "Song",
                artist = "Artist",
                album = "Album",
                durationMs = 180_000,
            ),
            listenedAtEpochSeconds = 1_700_000_000L,
        )
        assertTrue(body.contains("\"listen_type\":\"playing_now\""))
        assertFalse(body.contains("listened_at"))
        assertTrue(body.contains("\"artist_name\":\"Artist\""))
        assertTrue(body.contains("\"track_name\":\"Song\""))
        assertTrue(body.contains("\"release_name\":\"Album\""))
        assertTrue(body.contains("\"duration_ms\":180000"))
        assertTrue(body.contains("ECHOAndroid"))
    }

    @Test
    fun submitBodyIncludesListenedAtForSingleListens() {
        val body = listenBrainzSubmitBody(
            listenType = "single",
            track = ListenBrainzTrack(
                id = "t1",
                title = "Song",
                artist = "Artist",
                album = null,
                durationMs = 0,
            ),
            listenedAtEpochSeconds = 1_700_000_000L,
        )
        assertTrue(body.contains("\"listen_type\":\"single\""))
        assertTrue(body.contains("\"listened_at\":1700000000"))
        assertFalse(body.contains("release_name"))
    }

    @Test
    fun validateResponseRequiresValidFlagAndUserName() {
        assertEquals(
            "alice",
            listenBrainzUserNameFromValidateResponse("""{"valid":true,"user_name":"alice"}"""),
        )
        assertNull(listenBrainzUserNameFromValidateResponse("""{"valid":false,"user_name":"alice"}"""))
        assertNull(listenBrainzUserNameFromValidateResponse("""{"valid":true}"""))
    }
}
