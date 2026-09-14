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
    // Share the available width instead of moving secondary actions to another row.
    // Keep the play/pause slot larger and all controls on the same vertical center.
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlSlot {
            PlayerControlButton(
                leadingIcon, leadingDescription, onLeadingAction,
                touchSize = 48.dp, iconSize = 21.dp, tint = OnArt.copy(alpha = 0.55f),
            )
        }
        ControlSlot {
            PlayerControlButton(
                PlayerControlIcons.Previous,
                stringResource(L10nR.string.feature_player_previous_af0264),
                onClick = { haptics.tick(); onPrevious() },
                touchSize = 48.dp, iconSize = 29.dp, tint = OnArt.copy(alpha = 0.90f),
            )
        }
        ControlSlot(weight = 64f / 48f) {
            PlayerControlButton(
                if (isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
                stringResource(L10nR.string.feature_player_play_or_pause_37a70f),
                onClick = { haptics.confirm(); onPlayPause() },
                touchSize = 64.dp, iconSize = 44.dp, tint = MaterialTheme.colorScheme.primary,
            )
        }
        ControlSlot {
            PlayerControlButton(
                PlayerControlIcons.Next,
                stringResource(L10nR.string.feature_player_next_d67904),
                onClick = { haptics.tick(); onNext() },
                touchSize = 48.dp, iconSize = 29.dp, tint = OnArt.copy(alpha = 0.90f),
            )
        }
        if (onCast != null) {
            ControlSlot {
                PlayerControlButton(
                    PlayerControlIcons.Cast,
                    stringResource(
                        if (castActive) L10nR.string.feature_player_cast_active
                        else L10nR.string.feature_player_cast,
                    ),
                    onClick = { haptics.tick(); onCast() },
                    touchSize = 48.dp,
                    iconSize = 21.dp,
                    tint = if (castActive) MaterialTheme.colorScheme.primary else OnArt.copy(alpha = 0.55f),
                )
            }
        }
        ControlSlot {
            PlayerControlButton(
                PlayerControlIcons.Queue,
                stringResource(L10nR.string.feature_player_queue_37fa6a),
                onOpenQueue,
                touchSize = 48.dp, iconSize = 21.dp, tint = OnArt.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun RowScope.ControlSlot(weight: Float = 1f, content: @Composable () -> Unit) {
    Box(Modifier.weight(weight), contentAlignment = Alignment.Center) {
        content()
    }
}
