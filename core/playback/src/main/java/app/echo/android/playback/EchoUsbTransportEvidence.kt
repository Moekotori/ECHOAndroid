package app.echo.android.playback

import app.echo.android.model.playback.EchoUsbCompatibilityRule
import app.echo.android.model.playback.EchoUsbTransportDiagnostics
import app.echo.android.usbaudio.UsbExclusivePcmSession

/** Reads only the session's immutable cached snapshot: no USB I/O or lock on the UI thread. */
internal fun UsbExclusivePcmSession.transportEvidence(): EchoUsbTransportDiagnostics? {
    val s = transferSnapshot ?: return null
    return EchoUsbTransportDiagnostics(s.queuedFrames, s.minQueuedFrames, s.capacityFrames,
        s.sampleRateHz, s.bytesPerFrame, s.audioUrbs, s.feedbackUrbs, s.audioPacketErrors,
        s.feedbackPacketErrors, s.submitFailures, s.reapFailures, s.supplyGapCount,
        s.supplyGapFrames, s.invalidFeedbackCount, s.lastErrorSource, s.lastErrorCode,
        s.fatal, vendorId, productId, snapshotAtElapsedMs, sessionClosed)
}

internal fun UsbExclusivePcmSession.compatibilityEvidence(): List<EchoUsbCompatibilityRule> =
    quirks.matchedRules.map { EchoUsbCompatibilityRule(it.id, it.reason, it.verification) }

internal fun UsbExclusivePcmSession.volumeEvidence(): app.echo.android.model.playback.EchoUsbVolumeDiagnostics? =
    volumeState?.let { app.echo.android.model.playback.EchoUsbVolumeDiagnostics(it.requested, it.effective,
        it.checked, it.readbackVerified, it.hardwareDb, it.handoff, it.reason) }
