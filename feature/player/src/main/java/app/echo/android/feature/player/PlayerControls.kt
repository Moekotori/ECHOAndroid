package app.echo.android.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared 24-unit optical grid for the expanded player and compact dock. */
internal object PlayerControlIcons {
    val Play = vector("PlayerPlay") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 5.15f)
            quadTo(7f, 3.95f, 8.05f, 4.58f)
            lineTo(19.1f, 11.12f)
            quadTo(20.55f, 12f, 19.1f, 12.88f)
            lineTo(8.05f, 19.42f)
            quadTo(7f, 20.05f, 7f, 18.85f)
            close()
        }
    }
    val Pause = vector("PlayerPause") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 4.5f); lineTo(9.5f, 4.5f); quadTo(10.25f, 4.5f, 10.25f, 5.25f)
            lineTo(10.25f, 18.75f); quadTo(10.25f, 19.5f, 9.5f, 19.5f)
            lineTo(7f, 19.5f); quadTo(6.25f, 19.5f, 6.25f, 18.75f)
            lineTo(6.25f, 5.25f); quadTo(6.25f, 4.5f, 7f, 4.5f); close()
            moveTo(14.5f, 4.5f); lineTo(17f, 4.5f); quadTo(17.75f, 4.5f, 17.75f, 5.25f)
            lineTo(17.75f, 18.75f); quadTo(17.75f, 19.5f, 17f, 19.5f)
            lineTo(14.5f, 19.5f); quadTo(13.75f, 19.5f, 13.75f, 18.75f)
            lineTo(13.75f, 5.25f); quadTo(13.75f, 4.5f, 14.5f, 4.5f); close()
        }
    }
    val Previous = vector("PlayerPrevious") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
            moveTo(5.5f, 5.5f); lineTo(5.5f, 18.5f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(17.5f, 5.7f); quadTo(18.5f, 5.05f, 18.5f, 6.25f)
            lineTo(18.5f, 17.75f); quadTo(18.5f, 18.95f, 17.5f, 18.3f)
            lineTo(8.7f, 12.85f); quadTo(7.35f, 12f, 8.7f, 11.15f); close()
        }
    }
    val Next = vector("PlayerNext") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
            moveTo(18.5f, 5.5f); lineTo(18.5f, 18.5f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(6.5f, 5.7f); quadTo(5.5f, 5.05f, 5.5f, 6.25f)
            lineTo(5.5f, 17.75f); quadTo(5.5f, 18.95f, 6.5f, 18.3f)
            lineTo(15.3f, 12.85f); quadTo(16.65f, 12f, 15.3f, 11.15f); close()
        }
    }
    val Lyrics = vector("PlayerLyrics") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(5f, 6f); lineTo(19f, 6f)
            moveTo(5f, 11f); lineTo(16f, 11f)
            moveTo(5f, 16f); lineTo(12f, 16f)
            moveTo(16.5f, 15f); lineTo(19f, 15f); lineTo(19f, 18f); quadTo(19f, 20f, 17f, 20.5f)
        }
    }
    val Settings = vector("PlayerSettings") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 7f); lineTo(8f, 7f); moveTo(12f, 7f); lineTo(20f, 7f)
            moveTo(10f, 4.5f); lineTo(10f, 9.5f)
            moveTo(4f, 17f); lineTo(12f, 17f); moveTo(16f, 17f); lineTo(20f, 17f)
            moveTo(14f, 14.5f); lineTo(14f, 19.5f)
        }
    }
    val Queue = vector("PlayerQueue") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(4f, 12f); lineTo(12f, 12f)
            moveTo(4f, 18f); lineTo(10f, 18f)
            moveTo(16f, 11.5f); lineTo(21f, 15f); lineTo(16f, 18.5f); close()
        }
    }
    val Collapse = vector("PlayerCollapse") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
        }
    }

    private fun vector(name: String, content: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(content).build()
}

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
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, tween(130), label = "player-control-press")
    Box(
        modifier = Modifier.size(touchSize).clickable(
            interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(icon, animationSpec = tween(120), label = "player-control-icon") { current ->
            Icon(current, contentDescription = description, tint = tint,
                modifier = Modifier.size(iconSize).graphicsLayer { scaleX = scale; scaleY = scale })
        }
    }
}
