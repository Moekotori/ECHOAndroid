package app.echo.android.feature.settings

import app.echo.android.model.playback.*

internal enum class SignalSoundMenuState { Off, Enabled, Adjusted, Bypassed }

// Keep the saved navigation IDs for the existing pages. Slot 3 used to open DSP.
internal enum class SignalSoundTool(val panel: Int, val title: Int, val summary: Int) {
    Volume(5, R.string.volume_title, R.string.volume_summary),
    Equalizer(0, R.string.peq_workspace_title, R.string.signal_sound_eq_summary),
    Effects(3, R.string.sound_effects_title, R.string.signal_sound_effects_summary),
    HeadphoneCorrection(1, R.string.feature_settings_headphone_correction_491ce5, R.string.signal_sound_headphones_summary),
    HeadphoneSoundstage(8, R.string.signal_sound_soundstage_title, R.string.signal_sound_soundstage_summary),
    Channels(2, R.string.channel_balance, R.string.signal_sound_channels_summary),
    Mseb(4, R.string.mseb_title, R.string.signal_sound_mseb_summary),
}

/** Configuration badges, independent of whether playback is currently running. */
internal fun signalSoundMenuStates(
    equalizer: EchoEqualizerState,
    channels: EchoChannelBalanceState,
    dsp: EchoDspSettings,
    replayGainEnabled: Boolean,
    volume: EchoVolumeSettings,
    bypassed: Boolean,
): Map<SignalSoundTool, SignalSoundMenuState> {
    // Named parametric curves are also shown as the current correction in its editor.
    val correctionEnabled = equalizer.enabled && equalizer.parametric &&
        !equalizer.sourceLabel.isNullOrBlank() && equalizer.filters.isNotEmpty()
    val volumeAdjusted = volume.changesSamples || replayGainEnabled || dsp.limiterEnabled ||
        dsp.equalLoudnessEnabled || dsp.compressor != EchoCompressorMode.Off
    return SignalSoundTool.entries.associateWith { tool ->
        val enabled = when (tool) {
            SignalSoundTool.Volume -> volumeAdjusted
            SignalSoundTool.Equalizer -> equalizer.enabled
            SignalSoundTool.Effects -> dsp.soundEffect.enabled
            SignalSoundTool.HeadphoneCorrection -> correctionEnabled
            SignalSoundTool.HeadphoneSoundstage -> dsp.crossfeedEnabled || dsp.hrtfEnabled
            SignalSoundTool.Channels -> channels.enabled
            SignalSoundTool.Mseb -> equalizer.mseb.enabled
        }
        when {
            !enabled -> SignalSoundMenuState.Off
            bypassed -> SignalSoundMenuState.Bypassed
            tool == SignalSoundTool.Volume -> SignalSoundMenuState.Adjusted
            else -> SignalSoundMenuState.Enabled
        }
    }
}
