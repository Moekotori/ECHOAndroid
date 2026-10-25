package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.*

@Composable
internal fun SystemAudioEffectsControl(diagnostics: EchoPlaybackDiagnostics, localPlayback: Boolean, onOpen: () -> Unit) {
    val availability = diagnostics.systemEffectsAvailability(localPlayback)
    SettingsSectionCard(title = stringResource(R.string.effects_panel_title)) {
        SettingsActionRow(title = stringResource(R.string.effects_panel_open),
            detail = stringResource(when (availability) {
                EchoSystemEffectsAvailability.RemotePlayback -> R.string.effects_panel_remote
                EchoSystemEffectsAvailability.Available -> R.string.effects_panel_ready
                EchoSystemEffectsAvailability.DedicatedUsb -> R.string.effects_panel_usb
                EchoSystemEffectsAvailability.Offloaded -> R.string.effects_panel_offloaded
                EchoSystemEffectsAvailability.NoSession -> R.string.effects_panel_no_session
                EchoSystemEffectsAvailability.NoSystemOutput -> R.string.effects_panel_no_output
            }), enabled = availability == EchoSystemEffectsAvailability.Available, onClick = onOpen)
    }
}
