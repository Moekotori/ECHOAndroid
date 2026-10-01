package app.echo.android.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon as ChromeIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoChromeColors

/** One continuous shape, so rapid reversals never stack translucent play/pause icons. */
@Composable
internal fun MiniPlayerPlayPauseIcon(playing: Boolean, motionEnabled: Boolean) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val chrome = echoChromeColors()
    val morph = animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = when {
            !motionEnabled -> snap()
            lightweight -> tween(90, easing = EchoMotion.Silk)
            else -> EchoMotion.silkFloat(240)
        },
        label = "mini-play-pause-morph",
    )
    Box(Modifier.size(28.dp).drawWithCache {
        // Paths and coordinate buffers belong to this drawing node, not to playback ticks.
        val left = Path()
        val right = Path()
        val leftPoints = FloatArray(8)
        val rightPoints = FloatArray(8)
        val scale = size.width / 24f
        onDrawBehind {
            val progress = morph.value.coerceIn(0f, 1f)
            morphQuad(leftPoints, PlayShape, LeftPauseShape, progress, scale)
            morphQuad(rightPoints, HiddenRightShape, RightPauseShape, progress, scale)
            roundedQuad(left, leftPoints)
            roundedQuad(right, rightPoints)
            drawPath(left, chrome.content)
            drawPath(right, chrome.content)
        }
    })
}

private val PlayShape = floatArrayOf(7f, 5f, 20f, 12f, 20f, 12f, 7f, 19f)
private val LeftPauseShape = floatArrayOf(6.5f, 5.5f, 9.5f, 5.5f, 9.5f, 18.5f, 6.5f, 18.5f)
private val HiddenRightShape = floatArrayOf(15.5f, 8f, 15.5f, 8f, 15.5f, 16f, 15.5f, 16f)
private val RightPauseShape = floatArrayOf(14.5f, 5.5f, 17.5f, 5.5f, 17.5f, 18.5f, 14.5f, 18.5f)

private fun morphQuad(output: FloatArray, from: FloatArray, to: FloatArray, progress: Float, scale: Float) {
    for (index in output.indices) output[index] = (from[index] + (to[index] - from[index]) * progress) * scale
}

private fun roundedQuad(path: Path, points: FloatArray) {
    path.rewind()
    val cut = 0.12f
    path.moveTo(points[0] + (points[6] - points[0]) * cut, points[1] + (points[7] - points[1]) * cut)
    for (corner in 0..3) {
        val current = corner * 2
        val previous = ((corner + 3) % 4) * 2
        val next = ((corner + 1) % 4) * 2
        val x = points[current]
        val y = points[current + 1]
        path.lineTo(x + (points[previous] - x) * cut, y + (points[previous + 1] - y) * cut)
        path.quadraticTo(x, y, x + (points[next] - x) * cut, y + (points[next + 1] - y) * cut)
    }
    path.close()
}

@Composable
internal fun MiniPlayerActionButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    motionEnabled: Boolean,
    rotation: Float = 0f,
    dockExpansion: State<Float>? = null,
) {
    val chrome = echoChromeColors()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val rotationState = if (dockExpansion == null) animateFloatAsState(
        targetValue = rotation,
        animationSpec = when {
            !motionEnabled -> snap()
            lightweight -> tween(90, easing = EchoMotion.Silk)
            else -> EchoMotion.silkFloat(280)
        },
        label = "mini-dock-chevron",
    ) else null
    Box(
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
            .miniPlayerPress(motionEnabled = motionEnabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ChromeIcon(icon, description, tint = chrome.secondary,
            modifier = Modifier.size(22.dp).graphicsLayer {
                rotationZ = dockExpansion?.let { 180f * (1f - it.value.coerceIn(0f, 1f)) }
                    ?: rotationState?.value ?: rotation
            })
    }
}

/** Animate only the visual layer; keep the 48 dp hit target stationary. */
@Composable
internal fun Modifier.miniPlayerPress(
    enabled: Boolean = true,
    motionEnabled: Boolean,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val scale = animateFloatAsState(
        targetValue = if (pressed && enabled && motionEnabled && !lightweight) 0.94f else 1f,
        animationSpec = if (!motionEnabled || lightweight) snap() else EchoMotion.silkFloat(if (pressed) 140 else 240),
        label = "mini-control-press",
    )
    return clickable(interactionSource = source, indication = null, enabled = enabled,
        role = Role.Button, onClick = onClick).graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        alpha = if (!enabled) 0.38f else if (pressed) 0.78f else 1f
    }
}

internal fun miniPlayerMotionDuration(defaultMs: Int, lightweight: Boolean): Int =
    if (lightweight) (defaultMs * 0.48f).toInt().coerceIn(90, defaultMs) else defaultMs
