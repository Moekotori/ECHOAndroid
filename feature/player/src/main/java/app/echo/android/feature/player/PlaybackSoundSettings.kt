package app.echo.android.feature.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoChannelBalanceState
import app.echo.android.model.playback.EchoPlaybackStatus

/** Player UI destinations; the app maps these to settings without a feature dependency. */
enum class PlaybackSoundDestination { Equalizer, Headphones, Balance, Output }

@Composable
fun PlaybackSoundSettings(
    status: EchoPlaybackStatus,
    equalizer: EchoEqualizerState,
    balance: EchoChannelBalanceState,
    onOpen: (PlaybackSoundDestination) -> Unit,
) {
    val bypassed = status.diagnostics.usbBitPerfectEnabled
    val eqDetail = when {
        bypassed -> stringResource(R.string.playback_sound_bypassed)
        !equalizer.supported || !equalizer.available -> stringResource(R.string.playback_sound_unavailable)
        !equalizer.enabled -> stringResource(R.string.feature_player_off_12ac24)
        else -> equalizer.sourceLabel ?: equalizer.presetName
    }
    PlaybackSettingsSection(Icons.Rounded.GraphicEq, stringResource(R.string.playback_sound_tools), "") {
        PlaybackActionRow(Icons.Rounded.GraphicEq, stringResource(R.string.playback_equalizer),
            onClick = { onOpen(PlaybackSoundDestination.Equalizer) }, detail = eqDetail)
        PlaybackActionRow(Icons.Rounded.Headphones, stringResource(R.string.playback_headphones),
            onClick = { onOpen(PlaybackSoundDestination.Headphones) },
            detail = stringResource(R.string.playback_headphones_hint))
        PlaybackActionRow(Icons.Rounded.Tune, stringResource(R.string.playback_balance),
            onClick = { onOpen(PlaybackSoundDestination.Balance) },
            detail = stringResource(when {
                bypassed -> R.string.playback_sound_bypassed
                balance.enabled -> R.string.feature_player_enabled_889420
                else -> R.string.feature_player_off_12ac24
            }))
        PlaybackActionRow(Icons.Rounded.Speaker, stringResource(R.string.playback_output),
            onClick = { onOpen(PlaybackSoundDestination.Output) },
            detail = status.diagnostics.usbDeviceName ?: stringResource(R.string.feature_player_system_output_856789))
    }
}
