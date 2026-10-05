package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoSleepTimerMode

@Composable
internal fun SettingsAccessibilityRest(
    status: EchoPlaybackStatus,
    onSetSleepTimer: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: () -> Unit,
    onCancelSleepTimer: () -> Unit,
) {
    val selected = when (status.sleepTimerMode) {
        EchoSleepTimerMode.Off -> "off"
        EchoSleepTimerMode.EndOfTrack -> "end"
        EchoSleepTimerMode.Timed -> status.sleepTimerMinutes?.toString().orEmpty()
    }
    // Use the player's timer; no additional clock, coroutine or spoken countdown.
    val remainingMs = status.sleepTimerRemainingMs.coerceAtLeast(0L)
    val remainingMinutes = (remainingMs / 60_000L + if (remainingMs % 60_000L > 0L) 1L else 0L)
        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val detail = when (status.sleepTimerMode) {
        EchoSleepTimerMode.Off -> stringResource(R.string.accessibility_rest_detail)
        EchoSleepTimerMode.EndOfTrack -> stringResource(R.string.accessibility_rest_end_detail)
        EchoSleepTimerMode.Timed -> pluralStringResource(R.plurals.accessibility_rest_remaining,
            remainingMinutes, remainingMinutes)
    }
    SettingsSectionCard(title = stringResource(R.string.accessibility_rest)) {
        SettingsChoiceGroupRow(
            title = stringResource(R.string.accessibility_rest_timer),
            detail = detail,
            selectedValue = selected,
            options = listOf(
                SettingsChoiceOption("off", stringResource(R.string.accessibility_rest_off)),
                SettingsChoiceOption("15", stringResource(R.string.accessibility_rest_minutes, 15)),
                SettingsChoiceOption("30", stringResource(R.string.accessibility_rest_minutes, 30)),
                SettingsChoiceOption("60", stringResource(R.string.accessibility_rest_minutes, 60)),
                SettingsChoiceOption("end", stringResource(R.string.accessibility_rest_end)),
            ),
            onOptionSelected = { value -> when (value) {
                "off" -> onCancelSleepTimer()
                "end" -> onSetSleepTimerEndOfTrack()
                else -> value.toIntOrNull()?.let(onSetSleepTimer)
            } },
        )
    }
}
