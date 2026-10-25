package app.echo.android.playback

import app.echo.android.model.error.EchoDiagnosticEvents

/** Called by the service progress clock, only reading cached USB counters. Never does USB I/O. */
internal object EchoUsbCaptureTrace {
    private var lastSample = -1L
    fun capture(status: EchoUsbExclusiveSinkStatus?) {
        if (!EchoDiagnosticEvents.enabled) return
        val snapshot = status?.transferEvidence?.invoke() ?: return
        if (snapshot.sampledAtElapsedMs == lastSample) return
        lastSample = snapshot.sampledAtElapsedMs
        EchoDiagnosticEvents.record(EchoDiagnosticEvents.Event.UsbTransport, snapshot.queuedFrames, snapshot.minQueuedFrames ?: -1)
        EchoDiagnosticEvents.record(EchoDiagnosticEvents.Event.UsbSupply, snapshot.supplyGapCount, snapshot.supplyGapFrames)
        EchoDiagnosticEvents.record(EchoDiagnosticEvents.Event.UsbAudioPackets, snapshot.audioPacketErrors, snapshot.audioUrbs)
        EchoDiagnosticEvents.record(EchoDiagnosticEvents.Event.UsbFeedbackPackets, snapshot.feedbackPacketErrors, snapshot.feedbackUrbs)
        EchoDiagnosticEvents.record(EchoDiagnosticEvents.Event.UsbUrbs, snapshot.submitFailures, snapshot.reapFailures)
        EchoDiagnosticEvents.record(EchoDiagnosticEvents.Event.UsbFeedbackClock, snapshot.invalidFeedbackCount, snapshot.lastErrorCode?.toLong() ?: 0)
    }
}
