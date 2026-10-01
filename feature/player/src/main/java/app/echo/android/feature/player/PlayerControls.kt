package app.echo.android.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import app.echo.android.design.EchoIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode

/** Transparent hit area: play/pause never gains a circular surface, border or ripple. */
@Composable
internal fun PlayerControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    touchSize: Dp = 52.dp,
    iconSize: Dp = 26.dp,
    tint: Color = OnArt.copy(alpha = 0.86f),
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val scale = animateFloatAsState(
        if (pressed && !lightweight) 0.88f else 1f,
        if (lightweight) snap() else spring(dampingRatio = if (pressed) 1f else 0.72f, stiffness = 650f),
        label = "player-control-press",
    )
    Box(
        modifier = Modifier.size(touchSize).clickable(
            interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(tween(if (lightweight) 0 else 180)) +
                        scaleIn(initialScale = if (lightweight) 1f else 0.78f,
                            animationSpec = EchoMotion.silkFloat(240)),
                    initialContentExit = fadeOut(tween(if (lightweight) 0 else 90)) +
                        scaleOut(targetScale = if (lightweight) 1f else 0.88f,
                            animationSpec = EchoMotion.silkFloat(180)),
                    sizeTransform = null,
                )
            },
            contentAlignment = Alignment.Center,
            label = "player-control-icon",
        ) { current ->
            EchoIcon(current, contentDescription = description, tint = tint,
                modifier = Modifier.size(iconSize).graphicsLayer { scaleX = scale.value; scaleY = scale.value })
        }
    }
}
