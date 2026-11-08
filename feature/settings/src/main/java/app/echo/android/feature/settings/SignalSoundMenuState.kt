package app.echo.android.feature.settings

import app.echo.android.model.playback.*

internal enum class SignalSoundMenuState { Off, Enabled, Adjusted, Bypassed }

/** Configuration badges, independent of whether playback is currently running. */
internal fun signalSoundMenuStates(
    equalizer: EchoEqualizerState,
    channels: EchoChannelBalanceState,
    dsp: EchoDspSettings,
    replayGainEnabled: Boolean,
    volume: EchoVolumeSettings,
    bypassed: Boolean,
): List<SignalSoundMenuState> {
    val dspEnabled = replayGainEnabled || dsp.limiterEnabled || dsp.crossfeedEnabled ||
        dsp.compressor != EchoCompressorMode.Off
    // Named parametric curves are also shown as the current correction in its editor.
    val correctionEnabled = equalizer.enabled && equalizer.parametric &&
        !equalizer.sourceLabel.isNullOrBlank() && equalizer.filters.isNotEmpty()
    val volumeAdjusted = volume.changesSamples || replayGainEnabled || dsp.limiterEnabled
    return listOf(
        equalizer.enabled,
        correctionEnabled,
        channels.enabled,
        dspEnabled,
        equalizer.mseb.enabled,
        volumeAdjusted,
    ).mapIndexed { index, enabled ->
        when {
            !enabled -> SignalSoundMenuState.Off
            bypassed -> SignalSoundMenuState.Bypassed
            index == 5 -> SignalSoundMenuState.Adjusted
            else -> SignalSoundMenuState.Enabled
        }
    }
}
