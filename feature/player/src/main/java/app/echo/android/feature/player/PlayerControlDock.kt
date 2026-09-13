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
    @Composable
    fun Transport() {
        Row(
            Modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerControlButton(
                PlayerControlIcons.Previous,
                stringResource(L10nR.string.feature_player_previous_af0264),
                onClick = { haptics.tick(); onPrevious() },
                touchSize = 48.dp, iconSize = 29.dp, tint = OnArt.copy(alpha = 0.90f),
            )
            PlayerControlButton(
                if (isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
                stringResource(L10nR.string.feature_player_play_or_pause_37a70f),
                onClick = { haptics.confirm(); onPlayPause() },
                touchSize = 64.dp, iconSize = 44.dp, tint = MaterialTheme.colorScheme.primary,
            )
            PlayerControlButton(
                PlayerControlIcons.Next,
                stringResource(L10nR.string.feature_player_next_d67904),
                onClick = { haptics.tick(); onNext() },
                touchSize = 48.dp, iconSize = 29.dp, tint = OnArt.copy(alpha = 0.90f),
            )
        }
    }
    @Composable
    fun Leading() {
        PlayerControlButton(leadingIcon, leadingDescription, onLeadingAction,
            touchSize = 48.dp, iconSize = 21.dp, tint = OnArt.copy(alpha = 0.55f))
    }
    @Composable
    fun Trailing() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onCast != null) {
                PlayerControlButton(
                    PlayerControlIcons.Cast,
                    stringResource(
                        if (castActive) L10nR.string.feature_player_cast_active
                        else L10nR.string.feature_player_cast,
                    ),
                    onClick = { haptics.tick(); onCast() },
                    touchSize = 44.dp,
                    iconSize = 21.dp,
                    tint = if (castActive) MaterialTheme.colorScheme.primary else OnArt.copy(alpha = 0.55f),
                )
            }
            PlayerControlButton(PlayerControlIcons.Queue, stringResource(L10nR.string.feature_player_queue_37fa6a), onOpenQueue,
                touchSize = 48.dp, iconSize = 21.dp, tint = OnArt.copy(alpha = 0.55f))
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // 48 + (48 + 64 + 48 + 20 spacing) + 48 + optional 44 for cast.
        val requiredWidth = if (onCast != null) 320.dp else 276.dp
        if (maxWidth < requiredWidth) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Transport()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Leading()
                    Trailing()
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Leading()
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Transport() }
                Trailing()
            }
        }
    }
}

