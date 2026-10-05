package app.echo.android.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoDropdownMenuItem
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoClickable
import app.echo.android.design.echoIconColor
import app.echo.android.model.playback.EchoRepeatMode

internal enum class QueuePlaybackMode(val repeatMode: EchoRepeatMode, val shuffleEnabled: Boolean) {
    Sequential(EchoRepeatMode.Off, false),
    RepeatAll(EchoRepeatMode.All, false),
    RepeatOne(EchoRepeatMode.One, false),
    Shuffle(EchoRepeatMode.All, true);

    companion object {
        fun from(repeatMode: EchoRepeatMode, shuffleEnabled: Boolean): QueuePlaybackMode = when {
            repeatMode == EchoRepeatMode.One -> RepeatOne
            shuffleEnabled -> Shuffle
            repeatMode == EchoRepeatMode.All -> RepeatAll
            else -> Sequential
        }
    }
}

internal fun selectQueuePlaybackMode(
    mode: QueuePlaybackMode,
    repeatMode: EchoRepeatMode,
    shuffleEnabled: Boolean,
    onSetRepeatMode: (EchoRepeatMode) -> Unit,
    onToggleShuffle: () -> Unit,
) {
    if (shuffleEnabled != mode.shuffleEnabled) onToggleShuffle()
    if (repeatMode != mode.repeatMode) onSetRepeatMode(mode.repeatMode)
}

@Composable
internal fun QueuePlaybackModeControl(
    repeatMode: EchoRepeatMode,
    shuffleEnabled: Boolean,
    onSetRepeatMode: (EchoRepeatMode) -> Unit,
    onToggleShuffle: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = QueuePlaybackMode.from(repeatMode, shuffleEnabled)
    val ink = echoIconColor()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val chevronRotation = if (lightweight) {
        if (expanded) 180f else 0f
    } else animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = EchoMotion.silkFloat(240),
        label = "queue-mode-chevron",
    ).value
    Box {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ink.copy(alpha = 0.045f))
                .echoClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.feature_player_playback_mode),
                    onClick = { expanded = true },
                )
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (lightweight) {
                QueueModeLabel(current)
            } else AnimatedContent(
                targetState = current,
                transitionSpec = {
                    (fadeIn(tween(240, easing = EchoMotion.Silk)) +
                        slideInVertically(EchoMotion.silkOffset(320)) { it / 2 }) togetherWith
                        (fadeOut(tween(160, easing = EchoMotion.SilkExit)) +
                            slideOutVertically(EchoMotion.silkOffset(280)) { -it / 2 }) using
                        SizeTransform(clip = false) { _, _ -> EchoMotion.silkSize(320) }
                },
                contentAlignment = Alignment.CenterStart,
                label = "queue-mode-label",
            ) { mode -> QueueModeLabel(mode) }
            EchoIcon(Icons.Rounded.ExpandMore, null,
                Modifier.size(18.dp).graphicsLayer { rotationZ = chevronRotation }, tint = ink)
        }
        EchoDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QueuePlaybackMode.entries.forEach { mode ->
                EchoDropdownMenuItem(
                    text = { Text(stringResource(mode.label())) },
                    leadingIcon = { EchoIcon(mode.icon(), null, Modifier.size(21.dp), tint = ink) },
                    selected = mode == current,
                    onClick = {
                        expanded = false
                        selectQueuePlaybackMode(mode, repeatMode, shuffleEnabled, onSetRepeatMode, onToggleShuffle)
                    },
                )
            }
        }
    }
}

@Composable
private fun QueueModeLabel(mode: QueuePlaybackMode) {
    val ink = echoIconColor()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        EchoIcon(mode.icon(), null, Modifier.size(21.dp), tint = ink)
        Text(stringResource(mode.label()), color = ink, style = MaterialTheme.typography.labelLarge)
    }
}

private fun QueuePlaybackMode.label(): Int = when (this) {
    QueuePlaybackMode.Sequential -> R.string.feature_player_in_order_47b60a
    QueuePlaybackMode.RepeatAll -> R.string.feature_player_repeat_all_b8cce1
    QueuePlaybackMode.RepeatOne -> R.string.feature_player_repeat_one_3df94f
    QueuePlaybackMode.Shuffle -> R.string.feature_player_shuffle
}

private fun QueuePlaybackMode.icon() = when (this) {
    QueuePlaybackMode.Sequential -> Icons.AutoMirrored.Rounded.QueueMusic
    QueuePlaybackMode.RepeatAll -> Icons.Rounded.Repeat
    QueuePlaybackMode.RepeatOne -> Icons.Rounded.RepeatOne
    QueuePlaybackMode.Shuffle -> Icons.Rounded.Shuffle
}
