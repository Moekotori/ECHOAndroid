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
        assertEquals(List(6) { SignalSoundMenuState.Off }, states())
        assertEquals(listOf(
            SignalSoundMenuState.Enabled, SignalSoundMenuState.Off,
            SignalSoundMenuState.Enabled, SignalSoundMenuState.Off,
            SignalSoundMenuState.Enabled, SignalSoundMenuState.Off,
        ), states(eq = EchoEqualizerState(enabled = true, mseb = EchoMsebState(enabled = true)),
            channels = EchoChannelBalanceState(enabled = true)))
    }

    @Test fun dspIncludesEveryProcessorAndVolumeIncludesItsAdvancedSwitches() {
        listOf(
            EchoDspSettings(limiterEnabled = true),
            EchoDspSettings(crossfeedEnabled = true),
            EchoDspSettings(compressor = EchoCompressorMode.Night),
        ).forEach { assertEquals(SignalSoundMenuState.Enabled, states(dsp = it)[3]) }
        assertEquals(SignalSoundMenuState.Enabled, states(replayGain = true)[3])
        assertEquals(SignalSoundMenuState.Adjusted, states(replayGain = true)[5])
        assertEquals(SignalSoundMenuState.Adjusted, states(dsp = EchoDspSettings(limiterEnabled = true))[5])
        assertEquals(SignalSoundMenuState.Adjusted, states(volume = EchoVolumeSettings(muted = true))[5])
    }

    @Test fun correctionRequiresAnEnabledNamedCurveAndBypassKeepsConfiguredBadges() {
        val eq = EchoEqualizerState(enabled = true, parametric = true, sourceLabel = "Headphones",
            filters = listOf(OpraEqBand("peak_dip", 100f, 2f, 1f, null)))
        assertEquals(SignalSoundMenuState.Enabled, states(eq = eq)[1])
        assertEquals(SignalSoundMenuState.Off, states(eq = eq.copy(enabled = false))[1])
        assertEquals(SignalSoundMenuState.Off, states(eq = eq.copy(sourceLabel = null))[1])
        assertEquals(listOf(
            SignalSoundMenuState.Bypassed, SignalSoundMenuState.Bypassed,
            SignalSoundMenuState.Off, SignalSoundMenuState.Off,
            SignalSoundMenuState.Off, SignalSoundMenuState.Bypassed,
        ), states(eq = eq, volume = EchoVolumeSettings(gainDb = -3f), bypassed = true))
    }
}
