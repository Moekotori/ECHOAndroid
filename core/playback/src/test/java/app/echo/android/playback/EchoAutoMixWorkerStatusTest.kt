package app.echo.android.playback

import app.echo.android.model.playback.EchoAutoMixStage
import app.echo.android.model.playback.EchoAutoMixStatus
import org.junit.Assert.*
import org.junit.Test

class EchoAutoMixWorkerStatusTest {
    @Test fun finishedWorkerCannotLeaveAReadyOrMixingDiagnostic() {
        for (stage in listOf(EchoAutoMixStage.Analyzing, EchoAutoMixStage.Waiting,
            EchoAutoMixStage.Preparing, EchoAutoMixStage.Armed, EchoAutoMixStage.Mixing)) {
            val stopped = EchoAutoMixStatus(stage, mode = "old mix", durationMs = 3000).workerStopped(false)!!
            assertEquals(EchoAutoMixStage.Bypassed, stopped.stage)
            assertEquals("ConditionsChanged", stopped.reason)
            assertFalse(stopped.playing)
            assertNull(stopped.mode)
            assertEquals(0L, stopped.durationMs)
        }
    }

    @Test fun finishedWorkerPreservesAnExplicitFailureOrBypassReason() {
        assertNull(EchoAutoMixStatus(EchoAutoMixStage.Failed, "DecodeFailed").workerStopped(true))
        assertNull(EchoAutoMixStatus(EchoAutoMixStage.Bypassed, "NoWindow").workerStopped(true))
        assertNull(EchoAutoMixStatus(EchoAutoMixStage.Idle).workerStopped(false))
    }
}
