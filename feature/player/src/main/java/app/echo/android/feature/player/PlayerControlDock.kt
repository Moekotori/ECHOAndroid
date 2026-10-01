package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.feature.player.R as L10nR

@Composable
internal fun NowPlayingControlDock(
    isPlaying: Boolean,
    leadingIcon: ImageVector,
    leadingDescription: String,
    onLeadingAction: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenQueue: () -> Unit,
    onCast: (() -> Unit)? = null,
    castActive: Boolean = false,
) {
    val haptics = rememberEchoHapticPerformer()
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerControlButton(
                PlayerControlIcons.Previous,
                stringResource(L10nR.string.feature_player_previous_af0264),
                onClick = { haptics.tick(); onPrevious() },
                touchSize = 56.dp, iconSize = 32.dp, tint = OnArt.copy(alpha = 0.90f),
            )
            PlayerControlButton(
                if (isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
                stringResource(L10nR.string.feature_player_play_or_pause_37a70f),
                onClick = { haptics.confirm(); onPlayPause() },
                touchSize = 72.dp, iconSize = 44.dp, tint = MaterialTheme.colorScheme.primary,
            )
            PlayerControlButton(
                PlayerControlIcons.Next,
                stringResource(L10nR.string.feature_player_next_d67904),
                onClick = { haptics.tick(); onNext() },
                touchSize = 56.dp, iconSize = 32.dp, tint = OnArt.copy(alpha = 0.90f),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerControlButton(
                leadingIcon, leadingDescription, onLeadingAction,
                touchSize = 48.dp, iconSize = 22.dp, tint = OnArtMuted,
            )
            if (onCast != null) {
                PlayerControlButton(
                    PlayerControlIcons.Cast,
                    stringResource(
                        if (castActive) L10nR.string.feature_player_cast_active
                        else L10nR.string.feature_player_cast,
                    ),
                    onClick = { haptics.tick(); onCast() },
                    touchSize = 48.dp,
                    iconSize = 23.dp,
                    tint = if (castActive) MaterialTheme.colorScheme.primary else OnArtMuted,
                )
            }
            PlayerControlButton(
                PlayerControlIcons.Queue,
                stringResource(L10nR.string.feature_player_queue_37fa6a),
                onOpenQueue,
                touchSize = 48.dp, iconSize = 22.dp, tint = OnArtMuted,
            )
        }
    }
}
