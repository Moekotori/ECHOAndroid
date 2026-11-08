package app.echo.android.model.lyrics

import app.echo.android.model.playback.EchoOutputDeviceKind
import app.echo.android.model.settings.EchoLyricsOptions
import org.junit.Assert.*
import org.junit.Test

class EchoLyricsOutputTimingTest {
    @Test fun switchingAwayFromBluetoothRestoresTheUncompensatedClock() {
        for (kind in EchoOutputDeviceKind.entries) {
            val delay = EchoLyricsOutputTiming.effectiveDelayMs(300, kind.id)
            assertEquals(if (kind == EchoOutputDeviceKind.Bluetooth) 700L else 1_000L,
                EchoLyricsOutputTiming.positionMs(1_000, delay, 1f))
        }
    }

    @Test fun outputLatencyIsWallTimeEvenDuringSpeedChanges() {
        assertEquals(700L, EchoLyricsOutputTiming.positionMs(1_000, 300, 1f))
        assertEquals(400L, EchoLyricsOutputTiming.positionMs(1_000, 300, 2f))
        assertEquals(850L, EchoLyricsOutputTiming.positionMs(1_000, 300, 0.5f))
        assertEquals(700L, EchoLyricsOutputTiming.positionMs(1_000, 300, Float.NaN))
        assertEquals(-300L, EchoLyricsOutputTiming.positionMs(0, 300, 1f))
    }

    @Test fun compensationAddsToSongTimingWithoutChangingTheDocument() {
        // This lyric already includes the user's +200 ms song correction.
        val lyric = EchoLyrics(lines = listOf(EchoLyricLine(1_200, text = "line")), offsetMs = 200)
        assertTrue(EchoLyricsOutputTiming.positionMs(1_499, 300, 1f) < lyric.lines.single().startMs)
        assertEquals(lyric.lines.single().startMs, EchoLyricsOutputTiming.positionMs(1_500, 300, 1f))
        assertEquals(1_200L, lyric.lines.single().startMs) // The seek target remains unchanged.
        assertEquals(200L, lyric.offsetMs)
    }

    @Test fun olderSettingsDefaultToOffAndInvalidValuesAreBounded() {
        assertEquals(0, EchoLyricsOptions().bluetoothDelayMs)
        assertEquals(0, EchoLyricsOptions(bluetoothDelayMs = -1).normalized.bluetoothDelayMs)
        assertEquals(2_000, EchoLyricsOptions(bluetoothDelayMs = Int.MAX_VALUE).normalized.bluetoothDelayMs)
    }
}
