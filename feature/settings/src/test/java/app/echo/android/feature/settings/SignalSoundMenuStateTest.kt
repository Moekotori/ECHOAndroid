package app.echo.android.feature.settings

import app.echo.android.model.playback.*
import org.junit.Assert.assertEquals
import org.junit.Test

class SignalSoundMenuStateTest {
    private fun states(
        eq: EchoEqualizerState = EchoEqualizerState(),
        channels: EchoChannelBalanceState = EchoChannelBalanceState(),
        dsp: EchoDspSettings = EchoDspSettings(),
        replayGain: Boolean = false,
        volume: EchoVolumeSettings = EchoVolumeSettings(),
        bypassed: Boolean = false,
    ) = signalSoundMenuStates(eq, channels, dsp, replayGain, volume, bypassed)

    @Test fun badgesFollowSwitchesEvenWhenSettingsAreNeutral() {
        assertEquals(SignalSoundTool.entries.associateWith { SignalSoundMenuState.Off }, states())
        assertEquals(mapOf(
            SignalSoundTool.Equalizer to SignalSoundMenuState.Enabled,
            SignalSoundTool.Channels to SignalSoundMenuState.Enabled,
            SignalSoundTool.Mseb to SignalSoundMenuState.Enabled,
        ), states(eq = EchoEqualizerState(enabled = true, mseb = EchoMsebState(enabled = true)),
            channels = EchoChannelBalanceState(enabled = true)).filterValues { it != SignalSoundMenuState.Off })
    }

    @Test fun volumeBadgeIncludesLoudnessAndDynamics() {
        listOf(
            EchoDspSettings(limiterEnabled = true),
            EchoDspSettings(compressor = EchoCompressorMode.Night),
            EchoDspSettings(equalLoudnessEnabled = true),
        ).forEach { assertEquals(SignalSoundMenuState.Adjusted, states(dsp = it)[SignalSoundTool.Volume]) }
        assertEquals(SignalSoundMenuState.Adjusted, states(replayGain = true)[SignalSoundTool.Volume])
        assertEquals(SignalSoundMenuState.Adjusted, states(volume = EchoVolumeSettings(muted = true))[SignalSoundTool.Volume])
    }

    @Test fun effectsAndHeadphoneSoundstageHaveIndependentBadges() {
        val effects = EchoDspSettings(soundEffect = EchoSoundEffectSettings(enabled = true))
        assertEquals(SignalSoundMenuState.Enabled, states(dsp = effects)[SignalSoundTool.Effects])
        assertEquals(SignalSoundMenuState.Off, states(dsp = effects)[SignalSoundTool.HeadphoneSoundstage])
        listOf(effects.copy(hrtfEnabled = true), effects.copy(crossfeedEnabled = true)).forEach {
            assertEquals(SignalSoundMenuState.Enabled, states(dsp = it)[SignalSoundTool.HeadphoneSoundstage])
            val bypassed = states(dsp = it, bypassed = true)
            assertEquals(SignalSoundMenuState.Bypassed, bypassed[SignalSoundTool.Effects])
            assertEquals(SignalSoundMenuState.Bypassed, bypassed[SignalSoundTool.HeadphoneSoundstage])
            assertEquals(SignalSoundMenuState.Off, bypassed[SignalSoundTool.Volume])
        }
    }

    @Test fun correctionRequiresAnEnabledNamedCurveAndBypassKeepsConfiguredBadges() {
        val eq = EchoEqualizerState(enabled = true, parametric = true, sourceLabel = "Headphones",
            filters = listOf(OpraEqBand("peak_dip", 100f, 2f, 1f, null)))
        assertEquals(SignalSoundMenuState.Enabled, states(eq = eq)[SignalSoundTool.HeadphoneCorrection])
        assertEquals(SignalSoundMenuState.Off, states(eq = eq.copy(enabled = false))[SignalSoundTool.HeadphoneCorrection])
        assertEquals(SignalSoundMenuState.Off, states(eq = eq.copy(sourceLabel = null))[SignalSoundTool.HeadphoneCorrection])
        assertEquals(mapOf(
            SignalSoundTool.Equalizer to SignalSoundMenuState.Bypassed,
            SignalSoundTool.HeadphoneCorrection to SignalSoundMenuState.Bypassed,
            SignalSoundTool.Volume to SignalSoundMenuState.Bypassed,
        ), states(eq = eq, volume = EchoVolumeSettings(gainDb = -3f), bypassed = true)
            .filterValues { it != SignalSoundMenuState.Off })
    }
}
