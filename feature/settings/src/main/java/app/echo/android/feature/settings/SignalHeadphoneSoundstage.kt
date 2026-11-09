package app.echo.android.feature.settings

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoDspSettings
import kotlin.math.abs

@Composable
internal fun SignalHeadphoneSoundstage(
    settings: EchoDspSettings,
    enabled: Boolean,
    onSettings: (EchoDspSettings) -> Unit,
) {
    var crossfeedExpanded by rememberSaveable { mutableStateOf(settings.crossfeedEnabled) }
    var hrtfExpanded by rememberSaveable { mutableStateOf(settings.hrtfEnabled) }

    if (!enabled) SignalNote(stringResource(R.string.signal_bypass_detail))
    SignalDspSection(
        title = stringResource(R.string.dsp_crossfeed),
        detail = stringResource(R.string.dsp_crossfeed_detail),
        summary = stringResource(R.string.signal_crossfeed_summary, signalNumber(settings.crossfeedAmount * 100, 0)),
        checked = settings.crossfeedEnabled,
        enabled = enabled,
        expanded = crossfeedExpanded,
        onExpand = { crossfeedExpanded = !crossfeedExpanded },
        onChecked = {
            onSettings(settings.copy(crossfeedEnabled = it, hrtfEnabled = settings.hrtfEnabled && !it))
            if (it) crossfeedExpanded = true
        },
        onReset = { onSettings(settings.copy(crossfeedEnabled = false, crossfeedAmount = 0.3f)) },
    ) {
        val amounts = listOf(0.15f, 0.3f, 0.5f)
        DspChoices(listOf(stringResource(R.string.dsp_gentle), stringResource(R.string.dsp_natural), stringResource(R.string.dsp_strong)),
            amounts.indexOfFirst { abs(it - settings.crossfeedAmount) < 0.005f }, enabled) {
            onSettings(settings.copy(crossfeedAmount = amounts[it]))
        }
        DspValueSlider(stringResource(R.string.dsp_mix_amount), settings.crossfeedAmount, 0f..0.6f, enabled,
            { "${signalNumber(it * 100, 0)}%" }, { onSettings(settings.copy(crossfeedAmount = it)) },
            unit = "%", scale = 100f, decimals = 0)
    }
    SignalDspSection(
        title = stringResource(R.string.personal_hrtf_title),
        detail = stringResource(R.string.personal_hrtf_detail),
        summary = stringResource(R.string.personal_stereo_only),
        checked = settings.hrtfEnabled,
        enabled = enabled,
        expanded = hrtfExpanded,
        onExpand = { hrtfExpanded = !hrtfExpanded },
        onChecked = {
            onSettings(settings.copy(hrtfEnabled = it, crossfeedEnabled = settings.crossfeedEnabled && !it))
            if (it) hrtfExpanded = true
        },
        onReset = { onSettings(settings.copy(hrtfEnabled = false, hrtfProfile = 1, hrtfAmount = 1f)) },
    ) {
        DspChoices(listOf(stringResource(R.string.personal_narrow), stringResource(R.string.personal_standard), stringResource(R.string.personal_wide)),
            settings.hrtfProfile, enabled) { onSettings(settings.copy(hrtfProfile = it)) }
        DspValueSlider(stringResource(R.string.personal_hrtf_amount), settings.hrtfAmount, 0f..1f, enabled,
            { "${signalNumber(it * 100, 0)}%" }, { onSettings(settings.copy(hrtfAmount = it)) },
            unit = "%", scale = 100f, decimals = 0)
        SignalNote(stringResource(R.string.personal_hrtf_listen))
    }
}
