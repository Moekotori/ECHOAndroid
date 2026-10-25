package app.echo.android.model.playback

import org.junit.Assert.*
import org.junit.Test

class EchoAutoMixStatusTest {
    @Test fun prebufferedFramesNeverClaimMixingBeforeTheAudibleClock() {
        val armed = EchoAutoMixStatus(EchoAutoMixStage.Armed, startPositionMs = 1000)
        assertEquals(EchoAutoMixStage.Armed, armed.audibleProgress(900, true, 48000).stage)
        assertEquals(EchoAutoMixStage.Armed, armed.audibleProgress(1000, false, 48000).stage)
        assertEquals(EchoAutoMixStage.Armed, armed.audibleProgress(1000, true, 0).stage)
        assertEquals(EchoAutoMixStage.Mixing, armed.audibleProgress(1000, true, 1).stage)
    }
    @Test fun invalidAnalysisNumbersAreNotPresentedAsMeasurements() {
        val safe = EchoAutoMixStatus(outgoingBpm = Double.NaN, incomingBpm = -1.0, tempoConfidence = 1.2).normalized()
        assertNull(safe.outgoingBpm); assertNull(safe.incomingBpm); assertNull(safe.tempoConfidence)
    }
}
