package app.echo.android.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.model.settings.EchoLyricsOptions
import kotlin.math.roundToInt

@Composable
internal fun SettingsStatusLyricsRows(
    options: EchoLyricsOptions,
    permissionGranted: Boolean,
    onChange: (EchoLyricsOptions) -> Unit,
    onRequestPermission: () -> Unit,
) {
    var adjust by remember { mutableStateOf(false) }
    SettingsSwitchRow(
        title = stringResource(R.string.settings_notification_lyrics),
        detail = stringResource(R.string.settings_notification_lyrics_detail),
        checked = options.notificationEnabled,
        onCheckedChange = { onChange(options.copy(notificationEnabled = it)) },
    )
    SettingsSwitchRow(
        title = stringResource(R.string.settings_status_overlay_lyrics),
        detail = stringResource(R.string.settings_status_overlay_lyrics_detail),
        checked = options.statusOverlayEnabled,
        onCheckedChange = {
            onChange(options.copy(statusOverlayEnabled = it))
            if (it && !permissionGranted) onRequestPermission()
        },
    )
    if (options.statusOverlayEnabled) {
        if (!permissionGranted) SettingsActionRow(
            title = stringResource(R.string.settings_floating_lyrics_permission),
            detail = stringResource(R.string.settings_floating_lyrics_permission_detail),
            actionLabel = stringResource(R.string.settings_allow),
            onClick = onRequestPermission,
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_status_lyrics_layout),
            detail = stringResource(R.string.settings_status_lyrics_layout_detail),
            onClick = { adjust = true },
        )
    }
    val supported = Build.MANUFACTURER.equals("meizu", ignoreCase = true)
    SettingsSwitchRow(
        title = stringResource(R.string.settings_system_status_lyrics),
        detail = stringResource(if (supported) R.string.settings_system_status_lyrics_detail
            else R.string.settings_system_status_lyrics_unsupported),
        checked = options.systemStatusBarEnabled,
        // A restored preference on another device can always be turned off.
        enabled = supported || options.systemStatusBarEnabled,
        onCheckedChange = { onChange(options.copy(systemStatusBarEnabled = it)) },
    )
    SettingsSwitchRow(
        title = stringResource(R.string.settings_status_lyrics_hide_translation),
        detail = stringResource(R.string.settings_status_lyrics_hide_translation_detail),
        checked = options.statusHideTranslation,
        enabled = options.statusOverlayEnabled || options.systemStatusBarEnabled,
        onCheckedChange = { onChange(options.copy(statusHideTranslation = it)) },
    )
    if (!adjust) return
    AlertDialog(
        onDismissRequest = { adjust = false },
        title = { Text(stringResource(R.string.settings_status_lyrics_layout)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.settings_status_lyrics_preview),
                    fontSize = options.statusFontSp.sp, color = MaterialTheme.colorScheme.primary)
                SettingsSliderRow(
                    title = stringResource(R.string.settings_status_lyrics_font),
                    valueLabel = { "${it.roundToInt()} sp" }, value = options.statusFontSp,
                    valueRange = 9f..18f, steps = 8,
                    onValueChange = { onChange(options.copy(statusFontSp = it)) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.settings_status_lyrics_width),
                    valueLabel = { "${it.roundToInt()} dp" }, value = options.statusWidthDp.toFloat(),
                    valueRange = 80f..320f, steps = 23,
                    onValueChange = { onChange(options.copy(statusWidthDp = it.roundToInt())) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.settings_status_lyrics_x),
                    valueLabel = { "${it.roundToInt()} dp" }, value = options.statusOffsetXDp.toFloat(),
                    valueRange = 0f..320f, steps = 31,
                    onValueChange = { onChange(options.copy(statusOffsetXDp = it.roundToInt())) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.settings_status_lyrics_y),
                    valueLabel = { "${it.roundToInt()} dp" }, value = options.statusOffsetYDp.toFloat(),
                    valueRange = 0f..96f, steps = 23,
                    onValueChange = { onChange(options.copy(statusOffsetYDp = it.roundToInt())) },
                )
                SettingsActionRow(
                    title = stringResource(R.string.settings_status_lyrics_reset),
                    detail = stringResource(R.string.settings_status_lyrics_layout_detail),
                    onClick = { onChange(options.copy(statusFontSp = 12f, statusWidthDp = 180,
                        statusOffsetXDp = 80, statusOffsetYDp = 0)) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { adjust = false }) { Text(stringResource(R.string.settings_lyrics_apply)) } },
    )
}
