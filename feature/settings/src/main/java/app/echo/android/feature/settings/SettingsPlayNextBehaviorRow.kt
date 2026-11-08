package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoShuffleOptions

@Composable
internal fun SettingsPlayNextBehaviorRow(options: EchoShuffleOptions, onChange: (EchoShuffleOptions) -> Unit) {
    val value = options.normalized
    SettingsSectionCard(title = stringResource(R.string.settings_play_next_behavior)) {
        SettingsChoiceGroupRow(
            title = stringResource(R.string.settings_play_next_behavior), detail = stringResource(R.string.settings_play_next_behavior_detail),
            options = app.echo.android.model.playback.EchoPlayNextBehavior.entries.map {
                SettingsChoiceOption(it.id, stringResource(if (it == app.echo.android.model.playback.EchoPlayNextBehavior.Repeat)
                    R.string.settings_play_next_repeat else R.string.settings_play_next_move))
            }, selectedValue = value.playNextBehavior.id,
            onOptionSelected = { onChange(value.copy(playNextBehavior = app.echo.android.model.playback.EchoPlayNextBehavior.fromId(it))) },
        )
    }
}
