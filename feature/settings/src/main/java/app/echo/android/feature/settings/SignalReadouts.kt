package app.echo.android.feature.settings

import app.echo.android.model.playback.EchoBitPerfectState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.*
import kotlin.math.roundToInt

@Composable
internal fun playbackStateLabel(state: EchoPlaybackState): String =
    when (state) {
        EchoPlaybackState.Idle -> stringResource(R.string.state_idle)
        EchoPlaybackState.Loading -> stringResource(R.string.state_loading)
        EchoPlaybackState.Playing -> stringResource(R.string.state_playing)
        EchoPlaybackState.Paused -> stringResource(R.string.state_paused)
        EchoPlaybackState.Seeking -> stringResource(R.string.state_seeking)
        EchoPlaybackState.Buffering -> stringResource(R.string.state_buffering)
        EchoPlaybackState.Ended -> stringResource(R.string.state_ended)
        EchoPlaybackState.Stopped -> stringResource(R.string.state_stopped)
        EchoPlaybackState.Error -> stringResource(R.string.state_error)
    }

@Composable
internal fun repeatModeLabel(mode: EchoRepeatMode): String =
    when (mode) {
        EchoRepeatMode.Off -> stringResource(R.string.repeat_off)
        EchoRepeatMode.All -> stringResource(R.string.repeat_all)
        EchoRepeatMode.One -> stringResource(R.string.repeat_one)
    }

@Composable
internal fun commandLabel(command: String?): String =
    when (command?.lowercase()) {
        null, "idle" -> stringResource(R.string.state_idle)
        "play", "playpause" -> stringResource(R.string.command_play)
        "pause" -> stringResource(R.string.command_pause)
        "next" -> stringResource(R.string.command_next)
        "previous" -> stringResource(R.string.command_previous)
        "seek" -> stringResource(R.string.command_seek)
        "stop" -> stringResource(R.string.command_stop)
        else -> command
    }

@Composable
internal fun EchoPlaybackDiagnostics.fileFormatLabel(): String {
    if (isDsdSource()) {
        val parts = listOfNotNull(
            dsdFamilyLabel() ?: codec ?: stringResource(R.string.diag_unknown_codec),
            stringResource(R.string.diag_dsd_one_bit),
        )
        return parts.joinToString(" / ")
    }
    val parts = listOfNotNull(
        codec ?: stringResource(R.string.diag_unknown_codec),
        sampleRateHz?.let(::formatSampleRate),
        bitDepth?.let { "${it}bit" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: stringResource(R.string.diag_waiting_playback)
}

@Composable
internal fun EchoPlaybackDiagnostics.decodedFormatLabel(): String {
    if (isDsdSource()) {
        val parts = listOfNotNull(
            decodedSampleRateHz?.let(::formatSampleRate) ?: sampleRateHz?.let(::formatSampleRate),
            channelCount?.let(::formatChannels),
            bitDepth?.let { depth ->
                if (isDsdDopOutput()) "${depth}bit DoP" else "${depth}bit PCM"
            },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ")
            ?: stringResource(R.string.diag_dsd_converted)
    }
    val parts = listOfNotNull(
        decodedSampleRateHz?.let(::formatSampleRate) ?: sampleRateHz?.let(::formatSampleRate),
        channelCount?.let(::formatChannels),
        bitDepth?.let { "${it}bit PCM" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: stringResource(R.string.diag_waiting_decode)
}

@Composable
internal fun EchoPlaybackDiagnostics.processingLabel(): String =
    when {
        offloadActive -> stringResource(R.string.diag_hw_offload)
        usbBitPerfectActive -> "USB bit-perfect"
        else -> stringResource(R.string.diag_system_path)
    }

@Composable
internal fun EchoPlaybackDiagnostics.signalIntegrityLabel(
    equalizerState: EchoEqualizerState,
    channelBalance: EchoChannelBalanceState = EchoChannelBalanceState(),
): String =
    when {
        usbBitPerfectEnabled -> bitPerfectReadout(equalizerState)
        equalizerState.active && channelBalance.active -> stringResource(R.string.diag_integrity_eq_balance)
        equalizerState.active -> stringResource(R.string.diag_integrity_eq)
        channelBalance.active -> stringResource(R.string.diag_integrity_balance)
        usbBitPerfectActive -> stringResource(R.string.diag_integrity_usb)
        offloadActive -> stringResource(R.string.diag_integrity_offload)
        usbExclusiveEnabled && usbConnected -> stringResource(R.string.diag_integrity_usb_wait)
        else -> stringResource(R.string.diag_integrity_system)
    }

@Composable
internal fun EchoPlaybackDiagnostics.bitPerfectReadout(equalizerState: EchoEqualizerState): String =
    stringResource(when (bitPerfectState) {
        EchoBitPerfectState.Off -> R.string.bitperfect_off
        EchoBitPerfectState.Waiting -> R.string.bitperfect_waiting
        EchoBitPerfectState.Direct -> R.string.bitperfect_direct
        EchoBitPerfectState.UnsupportedSource -> R.string.bitperfect_source
        EchoBitPerfectState.UsbUnavailable -> R.string.bitperfect_usb
        EchoBitPerfectState.UnsupportedFormat -> R.string.bitperfect_format
        EchoBitPerfectState.ClockUnverified -> R.string.bitperfect_clock
        EchoBitPerfectState.VolumeChanged -> R.string.bitperfect_volume
        EchoBitPerfectState.TransportError -> R.string.bitperfect_transport
        EchoBitPerfectState.PlaybackError -> R.string.bitperfect_playback_error
    })

internal fun formatSampleRate(sampleRateHz: Int): String =
    if (sampleRateHz >= 1000) {
        "${formatEqNumber(sampleRateHz / 1000f)} kHz"
    } else {
        "$sampleRateHz Hz"
    }

internal fun formatChannels(channelCount: Int): String =
    when (channelCount) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${channelCount}ch"
    }

@Composable
internal fun outputDeviceKindLabel(kind: String): String =
    stringResource(
        when (EchoOutputDeviceKind.fromId(kind)) {
            EchoOutputDeviceKind.Speaker -> R.string.diag_output_speaker
            EchoOutputDeviceKind.Wired -> R.string.diag_output_wired
            EchoOutputDeviceKind.Bluetooth -> R.string.diag_output_bluetooth
            EchoOutputDeviceKind.Usb -> R.string.diag_output_usb
            EchoOutputDeviceKind.Other -> R.string.diag_output_other
            EchoOutputDeviceKind.System -> R.string.diag_output_system
        },
    )

@Composable
internal fun EchoPlaybackDiagnostics.outputDeviceReadout(): String {
    val kind = outputDeviceKindLabel(outputDeviceKind)
    val name = outputDeviceName?.trim()?.takeIf { it.isNotEmpty() }
    val codec = bluetoothCodec?.trim()?.takeIf { it.isNotEmpty() }
    return listOfNotNull(kind, name, codec).joinToString(" · ").ifBlank { outputRoute }
}

internal fun formatBitrate(bitrate: Int): String =
    if (bitrate >= 1_000_000) {
        "${formatEqNumber((bitrate / 100_000f).roundToInt() / 10f)} Mbps"
    } else {
        "${bitrate / 1000} kbps"
    }

@Composable
internal fun equalizerDetail(state: EchoEqualizerState): String =
    when {
        state.enabled && state.parametric ->
            state.sourceLabel?.let { "$it · PEQ" }
                ?: stringResource(R.string.diag_eq_parametric)
        state.enabled && state.supported -> "${state.presetName} · ${state.bands.size} bands"
        state.enabled && !state.available -> stringResource(R.string.diag_eq_wait_playback, state.presetName)
        state.enabled -> stringResource(R.string.diag_eq_wait_system, state.presetName)
        else -> stringResource(R.string.diag_eq_off, state.bands.size)
    }

internal fun formatEqFrequency(frequencyHz: Int): String =
    if (frequencyHz >= 1000) {
        "${formatEqNumber((frequencyHz / 100f).roundToInt() / 10f)} kHz"
    } else {
        "$frequencyHz Hz"
    }

internal fun formatEqGain(gainDb: Float): String {
    val rounded = (gainDb * 10f).roundToInt() / 10f
    val prefix = if (rounded > 0f) "+" else ""
    return "$prefix${formatEqNumber(rounded)} dB"
}

internal fun formatEqNumber(value: Float): String =
    if (value == value.roundToInt().toFloat()) {
        value.roundToInt().toString()
    } else {
        value.toString()
    }

