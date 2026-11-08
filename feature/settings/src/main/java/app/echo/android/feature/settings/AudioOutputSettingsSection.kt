package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoOutputDeviceKind
import app.echo.android.model.playback.EchoPlaybackStatus

/** A compact route readout uses observed output, independently of saved USB preferences. */
@Composable
internal fun AudioOutputSettingsSection(status: EchoPlaybackStatus, onOpenUsbOutput: () -> Unit) {
    val diagnostics = status.diagnostics
    val usb = status.usbOutputPresentation()
    val kind = if (usb.streaming) EchoOutputDeviceKind.Usb.id else diagnostics.outputDeviceKind
    val route = outputDeviceKindLabel(kind)
    val device = (if (usb.streaming) usb.deviceName else diagnostics.outputDeviceName)
        ?.takeIf { it.isNotBlank() }
    SettingsSectionCard(title = stringResource(R.string.audio_output_title)) {
        SettingsInfoRow(
            title = device ?: route,
            detail = when {
                usb.streaming || diagnostics.outputRouteVerified -> if (device == null) "" else route
                else -> stringResource(R.string.audio_output_pending)
            },
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_section_usb),
            detail = stringResource(R.string.audio_usb_output_summary, stringResource(usb.headlineResource())),
            onClick = onOpenUsbOutput,
        )
    }
}
