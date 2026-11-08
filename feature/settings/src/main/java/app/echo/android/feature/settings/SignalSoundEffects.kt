package app.echo.android.feature.settings

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.*

@Composable
internal fun SignalSoundEffects(settings: EchoSoundEffectSettings, runtime: EchoSoundEffectRuntimeStatus,
    enabled: Boolean, onSettings: (EchoSoundEffectSettings) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = when {
        !enabled -> R.string.sound_effects_bypassed
        !settings.enabled -> R.string.sound_effects_off
        runtime.state == EchoSoundEffectState.Active && runtime.preset == settings.preset -> R.string.sound_effects_active
        runtime.state == EchoSoundEffectState.Failed -> R.string.sound_effects_failed
        runtime.state == EchoSoundEffectState.UnsupportedFormat -> R.string.sound_effects_unsupported
        runtime.state == EchoSoundEffectState.Preparing -> R.string.sound_effects_preparing
        else -> R.string.sound_effects_waiting
    }
    SignalDspSection(title = stringResource(R.string.sound_effects_title), detail = stringResource(R.string.sound_effects_detail),
        summary = stringResource(settings.preset.labelResource()) + " · " + stringResource(state),
        checked = settings.enabled, enabled = enabled, expanded = expanded, onExpand = { expanded = !expanded },
        onChecked = { onSettings(settings.copy(enabled = it)); if (it) expanded = true },
        onReset = { onSettings(EchoSoundEffectSettings()) }) {
        val presets = EchoSoundEffectPreset.entries
        DspChoices(presets.map { stringResource(it.labelResource()) }, presets.indexOf(settings.preset), enabled) {
            onSettings(EchoSoundEffectSettings(true, presets[it]))
        }
        SignalNote(stringResource(R.string.sound_effects_safety))
    }
}

private fun EchoSoundEffectPreset.labelResource(): Int = when (this) {
    EchoSoundEffectPreset.Vocal -> R.string.sound_effect_vocal
    EchoSoundEffectPreset.Bass -> R.string.sound_effect_bass
    EchoSoundEffectPreset.Warm -> R.string.sound_effect_warm
    EchoSoundEffectPreset.Wide -> R.string.sound_effect_wide
    EchoSoundEffectPreset.Live -> R.string.sound_effect_live
    EchoSoundEffectPreset.Hall -> R.string.sound_effect_hall
    EchoSoundEffectPreset.Tape -> R.string.sound_effect_tape
    EchoSoundEffectPreset.Night -> R.string.sound_effect_night
    EchoSoundEffectPreset.Headphones -> R.string.sound_effect_headphones
    EchoSoundEffectPreset.Echo -> R.string.sound_effect_echo
    EchoSoundEffectPreset.Tube -> R.string.sound_effect_tube
    EchoSoundEffectPreset.Vinyl -> R.string.sound_effect_vinyl
    EchoSoundEffectPreset.AutoPan -> R.string.sound_effect_autopan
    EchoSoundEffectPreset.Deess -> R.string.sound_effect_deess
    EchoSoundEffectPreset.Tremolo -> R.string.sound_effect_tremolo
    EchoSoundEffectPreset.Phaser -> R.string.sound_effect_phaser
    EchoSoundEffectPreset.Transient -> R.string.sound_effect_transient
    EchoSoundEffectPreset.Flanger -> R.string.sound_effect_flanger
    EchoSoundEffectPreset.Telephone -> R.string.sound_effect_telephone
    EchoSoundEffectPreset.Digital -> R.string.sound_effect_digital
    EchoSoundEffectPreset.AutoWah -> R.string.sound_effect_autowah
    EchoSoundEffectPreset.RingMod -> R.string.sound_effect_ringmod
}
