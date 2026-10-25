package app.echo.android.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.playback.EchoSmartTransitionVisual
import app.echo.android.model.playback.PlaybackPositionState

/** Only this leaf reads progress. The service owns the plan and the playback timeline. */
@Composable
internal fun SmartTransitionOverlay(
    state: State<EchoSmartTransitionVisual?>?,
    positionState: State<PlaybackPositionState>?,
    currentTrackId: String?,
    currentArtwork: String?,
    isPlaying: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible || state == null || positionState == null) return
    val active = state.value?.takeIf { it.currentTrackId == currentTrackId }
    val memory = remember { SmartTransitionOverlayMemory() }
    val visibility = remember { MutableTransitionState(false) }
    if (active == null && visibility.isIdle && !visibility.currentState) {
        SideEffect { memory.clear() }
        // Do not subscribe to the position clock once the outgoing overlay is fully gone.
        return
    }
    val liveFrame = active?.let {
        val position = positionState.value.positionMs
        if (position < it.startPositionMs - 4_000) null else
            SmartTransitionOverlayFrame(it, currentArtwork, isPlaying, it.progressAt(position))
    }
    SideEffect {
        visibility.targetState = liveFrame != null
        if (liveFrame != null) memory.retain(liveFrame)
        else if (visibility.isIdle && !visibility.currentState) memory.clear()
    }
    // Freeze covers, labels and progress together until exit finishes.
    val frame = liveFrame ?: memory.frame ?: return
    val visual = frame.visual
    val targetProgress = frame.progress
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    AnimatedVisibility(visibility, modifier,
        enter = fadeIn(tween(if (lightweight) 0 else 260)) +
            slideInVertically(tween(if (lightweight) 0 else 420, easing = EchoMotion.Silk)) { -it / 4 },
        exit = fadeOut(tween(if (lightweight) 0 else 150)) +
            slideOutVertically(tween(if (lightweight) 0 else 200)) { -it / 6 }) {
        val progress by animateFloatAsState(targetProgress,
            if (lightweight || !frame.isPlaying) snap() else tween(180, easing = LinearEasing), label = "automix-handoff")
        val scheme = MaterialTheme.colorScheme
        val shape = RoundedCornerShape(22.dp)
        Surface(shape = shape, color = scheme.surfaceContainerHigh.copy(alpha = 0.97f),
            shadowElevation = if (lightweight) 0.dp else 8.dp,
            modifier = Modifier.fillMaxWidth().border(1.dp, scheme.outlineVariant.copy(alpha = 0.55f), shape)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(68.dp).height(48.dp)) {
                        EchoArtworkImage(frame.currentArtwork, null, Modifier.size(40.dp).align(Alignment.CenterStart)
                            .graphicsLayer { alpha = 1f - progress * 0.45f; rotationZ = -7f * (1f - progress) },
                            shape = RoundedCornerShape(10.dp), sizeClass = EchoArtworkSize.Tiny)
                        EchoArtworkImage(visual.nextTrack.artworkUri, null, Modifier.size(44.dp).align(Alignment.CenterEnd)
                            .graphicsLayer { scaleX = 0.92f + progress * 0.08f; scaleY = scaleX },
                            shape = RoundedCornerShape(11.dp), sizeClass = EchoArtworkSize.Tiny)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("AutoMix", color = scheme.onSurface, style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(stringResource(when {
                                !frame.isPlaying -> R.string.player_automix_paused
                                targetProgress <= 0f -> R.string.player_automix_ready
                                else -> R.string.player_automix_mixing
                            }), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(visual.nextTrack.title, color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (visual.nextTrack.artist.isNotBlank()) Text(visual.nextTrack.artist,
                            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    EchoIcon(Icons.Rounded.SyncAlt, null, Modifier.size(19.dp), tint = scheme.onSurfaceVariant)
                }
                val rail = scheme.outlineVariant.copy(alpha = 0.7f)
                val incoming = scheme.primary
                Canvas(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(rail)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }) {
                    if (progress > 0f) drawLine(incoming, Offset.Zero.copy(y = size.height / 2),
                        Offset(size.width * progress, size.height / 2), size.height, StrokeCap.Round)
                }
            }
        }
    }
}

