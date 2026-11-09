package app.echo.android.feature.settings

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoDspSettings

@Composable
internal fun SignalEqualLoudnessControl(settings: EchoDspSettings, enabled: Boolean, onSettings: (EchoDspSettings) -> Unit) {
    var loudnessExpanded by rememberSaveable { mutableStateOf(false) }
    SignalDspSection(
        title = stringResource(R.string.personal_loudness_title), detail = stringResource(R.string.personal_loudness_detail),
        summary = stringResource(R.string.personal_stereo_only), checked = settings.equalLoudnessEnabled,
        enabled = enabled, expanded = loudnessExpanded, onExpand = { loudnessExpanded = !loudnessExpanded },
        onChecked = { onSettings(settings.copy(equalLoudnessEnabled = it)); if (it) loudnessExpanded = true },
        onReset = { onSettings(settings.copy(equalLoudnessEnabled = false, equalLoudnessReferenceDb = -10f, equalLoudnessBassDb = 6f, equalLoudnessTrebleDb = 3f)) },
    ) {
        DspValueSlider(stringResource(R.string.personal_reference), settings.equalLoudnessReferenceDb, -30f..0f, enabled,
            { "${signalNumber(it)} dB" }, { onSettings(settings.copy(equalLoudnessReferenceDb = it)) }, unit = "dB")
        DspValueSlider(stringResource(R.string.personal_bass), settings.equalLoudnessBassDb, 0f..12f, enabled,
            { "${signalNumber(it)} dB" }, { onSettings(settings.copy(equalLoudnessBassDb = it)) }, unit = "dB")
        DspValueSlider(stringResource(R.string.personal_treble), settings.equalLoudnessTrebleDb, 0f..6f, enabled,
            { "${signalNumber(it)} dB" }, { onSettings(settings.copy(equalLoudnessTrebleDb = it)) }, unit = "dB")
        SignalNote(stringResource(R.string.personal_loudness_note))
    }
}
