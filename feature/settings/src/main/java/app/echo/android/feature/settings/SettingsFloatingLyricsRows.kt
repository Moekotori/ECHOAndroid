package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.settings.EchoFloatingLyricsSettings
import kotlin.math.roundToInt

/** 悬浮歌词的设置行，放在「歌词」分区里。 */
@Composable
internal fun SettingsFloatingLyricsRows(
    settings: EchoFloatingLyricsSettings,
    permissionGranted: Boolean,
    onChange: (EchoFloatingLyricsSettings) -> Unit,
    onRequestPermission: () -> Unit,
) {
    SettingsSwitchRow(
        title = stringResource(R.string.settings_floating_lyrics),
        detail = stringResource(R.string.settings_floating_lyrics_detail),
        checked = settings.enabled,
        onCheckedChange = { enabled ->
            onChange(settings.copy(enabled = enabled))
            if (enabled && !permissionGranted) onRequestPermission()
        },
    )
    if (!settings.enabled) return
    if (!permissionGranted) {
        SettingsActionRow(
            title = stringResource(R.string.settings_floating_lyrics_permission),
            detail = stringResource(R.string.settings_floating_lyrics_permission_detail),
            actionLabel = stringResource(R.string.settings_allow),
            onClick = onRequestPermission,
        )
    }
    SettingsSwitchRow(
        title = stringResource(R.string.settings_floating_lyrics_lock),
        detail = stringResource(R.string.settings_floating_lyrics_lock_detail),
        checked = settings.locked,
        onCheckedChange = { onChange(settings.copy(locked = it)) },
    )
    SettingsSliderRow(
        title = stringResource(R.string.settings_floating_lyrics_size),
        valueLabel = { "${(it * 100f).roundToInt()}%" },
        value = settings.fontScale,
        valueRange = EchoFloatingLyricsSettings.MinFontScale..EchoFloatingLyricsSettings.MaxFontScale,
        steps = 7,
        onValueChange = { onChange(settings.copy(fontScale = it)) },
    )
}
