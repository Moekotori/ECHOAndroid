package app.echo.android.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.*

@Composable
internal fun signalProcessingStage(
    status: EchoPlaybackStatus,
    equalizer: EchoEqualizerState,
    balance: EchoChannelBalanceState,
    dsp: EchoDspSettings,
): SignalPathStage {
    val bypassed = status.diagnostics.usbBitPerfectEnabled
    val unknown = stringResource(R.string.diag_unreported)
    val off = stringResource(R.string.path_disabled)
    val enabled = stringResource(R.string.path_enabled)
    val effects = buildList {
        if (equalizer.active) add(stringResource(R.string.feature_settings_equalizer_7ccb03))
        if (balance.active) add(stringResource(R.string.channel_balance))
        if (status.replayGainEnabled) add(stringResource(R.string.dsp_loudness))
        if (dsp.limiterEnabled) add(stringResource(R.string.dsp_limiter))
        if (dsp.crossfeedEnabled) add(stringResource(R.string.dsp_crossfeed))
        if (status.playbackSpeed != 1f || status.playbackPitch != 1f) add(stringResource(R.string.path_speed_pitch))
    }
    val facts = buildList {
        add(stringResource(R.string.feature_settings_equalizer_7ccb03) to when {
            !equalizer.enabled -> off
            !equalizer.available || !equalizer.supported -> stringResource(R.string.eq_waiting_pipeline)
            else -> equalizer.sourceLabel ?: equalizer.presetName
        })
        if (equalizer.enabled) {
            add(stringResource(R.string.eq_preamp) to formatEqGain(equalizer.preampDb))
            add(stringResource(R.string.path_eq_bands) to
                (if (equalizer.parametric) equalizer.filters.size else equalizer.bands.size).toString())
            add(stringResource(R.string.path_processing_rate) to
                (equalizer.processingSampleRateHz?.takeIf { it > 0 }?.let(::formatSampleRate) ?: unknown))
        }
        add(stringResource(R.string.channel_balance) to if (balance.enabled) channelPathDetail(balance) else off)
        if (balance.enabled) {
            add(stringResource(R.string.channel_balance_left_gain) to formatEqGain(balance.leftGainDb))
            add(stringResource(R.string.channel_balance_right_gain) to formatEqGain(balance.rightGainDb))
            add(stringResource(R.string.channel_balance_delay_left) to stringResource(R.string.channel_balance_delay_ms, balance.leftDelayMs))
            add(stringResource(R.string.channel_balance_delay_right) to stringResource(R.string.channel_balance_delay_ms, balance.rightDelayMs))
        }
        add(stringResource(R.string.dsp_loudness) to if (status.replayGainEnabled) enabled else off)
        if (status.replayGainEnabled) {
            add(stringResource(R.string.path_tag_gain) to (status.replayGainTrackGainDb?.let(::formatEqGain) ?: unknown))
            add(stringResource(R.string.path_gain_preamp) to formatEqGain(status.replayGainPreampDb))
        }
        add(stringResource(R.string.dsp_limiter) to if (dsp.limiterEnabled)
            stringResource(R.string.dsp_ceiling, dsp.limiterCeilingDb) else off)
        add(stringResource(R.string.dsp_crossfeed) to if (dsp.crossfeedEnabled)
            stringResource(R.string.path_crossfeed_amount, (dsp.crossfeedAmount * 100).toInt()) else off)
        add(stringResource(R.string.path_speed_pitch) to
            stringResource(R.string.path_speed_pitch_value, formatEqNumber(status.playbackSpeed), formatEqNumber(status.playbackPitch)))
    }
    return SignalPathStage(
        label = stringResource(R.string.diag_processing_layer),
        value = when {
            bypassed -> stringResource(R.string.bitperfect_bypass)
            effects.isEmpty() -> stringResource(R.string.path_no_adjustments)
            else -> effects.joinToString(" · ")
        },
        detail = stringResource(if (bypassed) R.string.path_bypass_settings_note else R.string.path_processing_settings_note),
        icon = Icons.Rounded.Tune,
        highlighted = false, // Settings alone do not establish active PCM processing.
        facts = facts,
    )
}

@Composable
private fun channelPathDetail(state: EchoChannelBalanceState): String {
    val b = state.normalized
    return buildList {
        if (kotlin.math.abs(b.balance) > 0.001f) add(stringResource(
            R.string.channel_balance_bias,
            stringResource(if (b.balance < 0) R.string.channel_balance_left else R.string.channel_balance_right),
            (kotlin.math.abs(b.balance) * 100).toInt(),
        ))
        if (b.swapLeftRight) add(stringResource(R.string.channel_balance_swap))
        if (b.invertLeft) add(stringResource(R.string.channel_balance_invert_left))
        if (b.invertRight) add(stringResource(R.string.channel_balance_invert_right))
        if (b.monoMode != EchoChannelBalanceMonoMode.Off) add(stringResource(when (b.monoMode) {
            EchoChannelBalanceMonoMode.Left -> R.string.channel_balance_mono_left
            EchoChannelBalanceMonoMode.Right -> R.string.channel_balance_mono_right
            else -> R.string.channel_balance_mono_sum
        }))
        if (b.leftBandGainsDb.any { kotlin.math.abs(it) >= 0.05f } || b.rightBandGainsDb.any { kotlin.math.abs(it) >= 0.05f })
            add(stringResource(R.string.channel_balance_bands))
    }.joinToString(" · ").ifBlank { stringResource(R.string.channel_balance_mono_off) }
}
