package app.echo.android.playback

import app.echo.android.model.playback.EchoSleepTimerMode
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoSleepTimerTilePolicyTest {
    @Test
    fun cyclesOffFifteenThirtySixtyEndThenOff() {
        assertEquals(
            EchoSleepTimerTileStep.Minutes15,
            EchoSleepTimerTilePolicy.next(EchoSleepTimerMode.Off, null),
        )
        assertEquals(
            EchoSleepTimerTileStep.Minutes30,
            EchoSleepTimerTilePolicy.next(EchoSleepTimerMode.Timed, 15),
        )
        assertEquals(
            EchoSleepTimerTileStep.Minutes60,
            EchoSleepTimerTilePolicy.next(EchoSleepTimerMode.Timed, 30),
        )
        assertEquals(
            EchoSleepTimerTileStep.EndOfTrack,
            EchoSleepTimerTilePolicy.next(EchoSleepTimerMode.Timed, 60),
        )
        assertEquals(
            EchoSleepTimerTileStep.Off,
            EchoSleepTimerTilePolicy.next(EchoSleepTimerMode.EndOfTrack, null),
        )
    }
}
