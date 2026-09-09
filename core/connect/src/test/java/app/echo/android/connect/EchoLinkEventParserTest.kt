package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemotePlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EchoLinkEventParserTest {
    private val endpoint = EchoRemoteEndpoint(
        id = "192.168.1.20:26789",
        name = "PC ECHO",
        host = "192.168.1.20",
        port = 26789,
        token = "abcdefghijklmnop",
        supportsV2Events = true,
    )

    @Test
    fun parsesNestedSnapshotAndQueue() {
        val message = parseEchoLinkEventData(
            """
            {
              "type": "playback.track.changed",
              "snapshot": {
                "playback": {
                  "state": "playing",
                  "positionMs": 1200,
                  "durationMs": 240000,
                  "volume": 0.5,
                  "track": { "id": "a", "title": "Song", "artist": "Artist" },
                  "queue": {
                    "currentTrackId": "a",
                    "items": [
                      { "id": "a", "title": "Song", "artist": "Artist", "durationMs": 240000 },
                      { "id": "b", "title": "Next", "artist": "Artist", "durationMs": 180000 }
                    ]
                  }
                }
              }
            }
            """.trimIndent(),
            endpoint,
        )
        assertNotNull(message)
        val playback = message!!.payload
        assertEquals(EchoRemotePlaybackState.Playing, playback.state)
        assertEquals(1200L, playback.positionMs)
        assertEquals("a", playback.track?.id)
        assertEquals("a", playback.queue.currentTrackId)
        assertEquals(2, playback.queue.items.size)
        assertEquals("Next", playback.queue.items[1].title)
    }
}
