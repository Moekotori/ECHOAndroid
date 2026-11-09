package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

@Composable
internal fun UsbOutputModeControl(strict: Boolean, onChange: (Boolean) -> Unit) {
    var confirming by remember(strict) { mutableStateOf(false) }
    if (confirming && !strict) {
        UsbPassthroughWarningDialog(
            onDismiss = { confirming = false },
            onConfirm = {
                confirming = false
                onChange(true)
            },
        )
    }
    SettingsSwitchRow(
        title = stringResource(R.string.settings_usb_bitperfect),
        detail = stringResource(R.string.usb_page_volume_note) +
            "\n${stringResource(R.string.settings_usb_bitperfect_detail)}",
        checked = strict,
        onCheckedChange = { enabled ->
            if (enabled) confirming = true else onChange(false)
        },
    )
}
