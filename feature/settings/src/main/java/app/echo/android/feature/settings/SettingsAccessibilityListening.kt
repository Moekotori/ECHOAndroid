package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoChannelBalance
import app.echo.android.model.playback.EchoChannelBalanceMonoMode
import app.echo.android.model.playback.EchoChannelBalanceState
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun SettingsAccessibilityListening(
    channelBalance: EchoChannelBalanceState,
    processingBypassed: Boolean,
    replayGainEnabled: Boolean,
    replayGainPreampDb: Float,
    pauseOnDisconnect: Boolean,
    resumeOnReconnect: Boolean,
    onChannelBalanceChange: (EchoChannelBalanceState) -> Unit,
    onReplayGainChange: (Boolean, Float) -> Unit,
    onPauseOnDisconnectChange: (Boolean) -> Unit,
    onResumeOnReconnectChange: (Boolean) -> Unit,
) {
    val monoActive = channelBalance.enabled && channelBalance.monoMode != EchoChannelBalanceMonoMode.Off
    SettingsSectionCard(title = stringResource(R.string.accessibility_listening)) {
        if (processingBypassed) SettingsInfoRow(
            title = stringResource(R.string.accessibility_processing_limited),
            detail = stringResource(R.string.accessibility_processing_limited_detail),
        )
        SettingsSwitchRow(
            title = stringResource(R.string.accessibility_mono),
            detail = stringResource(R.string.accessibility_mono_detail),
            checked = channelBalance.enabled && channelBalance.monoMode == EchoChannelBalanceMonoMode.Sum,
            enabled = !processingBypassed,
            onCheckedChange = { onChannelBalanceChange(channelBalance.withAccessibilityMono(it)) },
        )
        SettingsSliderRow(
            title = stringResource(R.string.accessibility_balance),
            valueLabel = { value ->
                val percent = (abs(value) * 100f).roundToInt()
                when {
                    percent == 0 -> stringResource(R.string.channel_balance_center)
                    value < 0f -> stringResource(R.string.channel_balance_bias,
                        stringResource(R.string.channel_balance_left), percent)
                    else -> stringResource(R.string.channel_balance_bias,
                        stringResource(R.string.channel_balance_right), percent)
                }
            },
            value = if (channelBalance.enabled) channelBalance.balance else 0f,
            valueRange = EchoChannelBalance.MinBalance..EchoChannelBalance.MaxBalance,
            steps = 19,
            enabled = !processingBypassed && !monoActive,
            onValueChange = { onChannelBalanceChange(channelBalance.withAccessibilityBalance(it)) },
        )
        if (monoActive) Text(
            stringResource(R.string.accessibility_balance_mono_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsActionRow(
            title = stringResource(R.string.accessibility_balance_reset),
            detail = stringResource(R.string.accessibility_balance_detail),
            enabled = !processingBypassed && channelBalance.balance != 0f,
            actionLabel = stringResource(R.string.channel_balance_center),
            onClick = { onChannelBalanceChange(channelBalance.copy(balance = 0f)) },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_replay_gain),
            detail = stringResource(R.string.accessibility_replay_gain_detail),
            checked = replayGainEnabled,
            enabled = !processingBypassed,
            onCheckedChange = { onReplayGainChange(it, replayGainPreampDb) },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_pause_on_disconnect),
            detail = stringResource(R.string.accessibility_disconnect_detail),
            checked = pauseOnDisconnect,
            onCheckedChange = onPauseOnDisconnectChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_resume_on_reconnect),
            detail = stringResource(R.string.accessibility_reconnect_detail),
            checked = resumeOnReconnect,
            enabled = pauseOnDisconnect,
            onCheckedChange = onResumeOnReconnectChange,
        )
    }
}

/** Keep the user's gain, routing, delay and EQ adjustments when editing these two controls. */
internal fun EchoChannelBalanceState.withAccessibilityMono(enabled: Boolean): EchoChannelBalanceState =
    copy(enabled = enabled || this.enabled,
        monoMode = if (enabled) EchoChannelBalanceMonoMode.Sum else EchoChannelBalanceMonoMode.Off)

internal fun EchoChannelBalanceState.withAccessibilityBalance(value: Float): EchoChannelBalanceState =
    copy(enabled = true, balance = EchoChannelBalance.clampBalance(value))
