package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.*

@Composable
internal fun AutoMixStatusReadout(status: EchoAutoMixStatus) {
    val stage = when {
        !status.playing && status.stage in listOf(EchoAutoMixStage.Armed, EchoAutoMixStage.Mixing) -> R.string.mix_status_paused
        else -> when (status.stage) {
            EchoAutoMixStage.Idle -> R.string.mix_status_idle
            EchoAutoMixStage.Bypassed -> R.string.mix_status_bypassed
            EchoAutoMixStage.Analyzing -> R.string.mix_status_analyzing
            EchoAutoMixStage.Waiting -> R.string.mix_status_waiting
            EchoAutoMixStage.Preparing -> R.string.mix_status_preparing
            EchoAutoMixStage.Armed -> R.string.mix_status_armed
            EchoAutoMixStage.Mixing -> R.string.mix_status_mixing
            EchoAutoMixStage.Failed -> R.string.mix_status_failed
        }
    }
    SignalReadout(stringResource(R.string.mix_status_title), stringResource(stage))
    status.reason?.let { reason ->
        val label = when (reason) {
            "Disabled" -> R.string.mix_reason_disabled
            "Lightweight" -> R.string.mix_reason_lightweight
            "BitPerfect", "UsbExclusive" -> R.string.mix_reason_usb
            "AlbumAdjacent" -> R.string.mix_reason_album
            "NextMissing" -> R.string.mix_reason_next
            "NotPlaying" -> R.string.mix_reason_not_playing
            "Live", "Remote" -> R.string.mix_reason_source
            "RepeatOne", "SleepEndOfTrack", "AbLoop" -> R.string.mix_reason_mode
            "PlaybackSpeed" -> R.string.mix_reason_speed
            "UnknownDuration", "ShortTrack", "SampleRateMismatch" -> R.string.mix_reason_format
            "StreamUnavailable" -> R.string.mix_reason_stream
            "PreparationFailed", "DecodeFailed" -> R.string.mix_reason_failed
            else -> R.string.mix_reason_window
        }
        SignalNote(stringResource(label))
    }
    status.mode?.let { SignalReadout(stringResource(R.string.mix_status_mode), it) }
    if (status.outgoingBpm != null || status.incomingBpm != null)
        SignalReadout("BPM", "${status.outgoingBpm?.toInt() ?: "—"} → ${status.incomingBpm?.toInt() ?: "—"}")
    status.tempoConfidence?.let { SignalReadout(stringResource(R.string.mix_status_confidence), "${(it * 100).toInt()}%") }
}
