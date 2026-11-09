package app.echo.android.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.LocalEchoPageInteractive
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.settings.EchoPlayerPageStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Scoped to the cover so each appearance can use the same adaptive lyrics slot. */
internal val LocalCoverLyricsPreview = compositionLocalOf<(@Composable () -> Unit)?> { null }

private const val MaxAnimatedLyricTravel = 1.05f

@Composable
internal fun CoverLyricsPreview(
    trackId: String,
    lyrics: EchoLyrics,
    position: State<Long>,
    visible: Boolean,
    onOpenLyrics: () -> Unit,
) {
    val animateChanges = visible && LocalEchoPageInteractive.current &&
        LocalWindowInfo.current.isWindowFocused && !LocalEchoEffectivePerformanceMode.current.isLightweight
    // Replacing the song or document must clear the old rows while the new index is prepared.
    key(trackId, lyrics) {
        val timeline by produceState<CoverLyricsTimeline?>(null) {
            value = withContext(Dispatchers.Default) { CoverLyricsTimeline(lyrics.lines).takeUnless { it.isEmpty } }
        }
        timeline?.let { CoverLyricsRows(it, position, animateChanges, onOpenLyrics) }
    }
}

@Composable
private fun CoverLyricsRows(
    timeline: CoverLyricsTimeline,
    position: State<Long>,
    animateChanges: Boolean,
    onOpenLyrics: () -> Unit,
) {
    // Only lyric boundaries invalidate the rows; the player owns the progress clock.
    val active by remember(timeline, position) {
        derivedStateOf(structuralEqualityPolicy()) { timeline.activeAt(position.value) }
    }
    val context by remember(timeline, position) {
        derivedStateOf(structuralEqualityPolicy()) { timeline.contextAt(position.value) }
    }
    val slot = timeline.slotOf(if (active >= 0) active else context)
    val scroll = if (animateChanges) {
        val viewport = remember { Animatable(slot.toFloat()) }
        LaunchedEffect(viewport, slot) {
            val target = slot.toFloat()
            val travel = abs(viewport.value - target)
            if (travel == 0f) return@LaunchedEffect
            // Keep at most one outgoing line at either edge. Jump straight to distant seeks.
            if (travel <= MaxAnimatedLyricTravel) {
                viewport.animateTo(target, tween(260, easing = EchoMotion.Silk))
            } else {
                viewport.snapTo(target)
            }
        }
        viewport
    } else null
    val singing = if (active >= 0) 1f else 0f
    val animatedSinging = if (animateChanges) {
        animateFloatAsState(singing, tween(180, easing = EchoMotion.Silk), label = "cover-lyric-focus")
    } else null
    val pageStyle = LocalPlayerPageStyle.current
    val fullCover = pageStyle == EchoPlayerPageStyle.FullCover
    val ink = if (pageStyle == EchoPlayerPageStyle.Classic || pageStyle.lyricsPreset.isAfterglow) {
        OnArt
    } else {
        remember(pageStyle) { playerPageTokens(pageStyle).ink }
    }
    val body = MaterialTheme.typography.bodyMedium
    val textStyle = body.copy(
        fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
        shadow = if (fullCover) FullCoverTextShadow else body.shadow,
    )
    val contextAlpha = if (fullCover) 0.76f else 0.64f
    val rowHeight = with(LocalDensity.current) { 22.sp.toDp() }
    val startIsLeft = LocalLayoutDirection.current == LayoutDirection.Ltr
    val radius = if (animateChanges) 2 else 1
    Box(
        Modifier.fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.feature_player_lyrics_b90c97),
                onClick = onOpenLyrics,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .height(rowHeight * 3)
            .clipToBounds()
            .drawBehind {
                val emphasis = animatedSinging?.value ?: singing
                if (emphasis > 0f) {
                    val inset = 1.dp.toPx()
                    val x = if (startIsLeft) inset else size.width - inset
                    val halfLength = 5.dp.toPx()
                    drawLine(
                        color = ink.copy(alpha = ink.alpha * emphasis),
                        start = Offset(x, center.y - halfLength),
                        end = Offset(x, center.y + halfLength),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            },
    ) {
        for (lineSlot in (slot - radius)..(slot + radius)) {
            key(lineSlot) {
                val line = remember(timeline, lineSlot) { timeline.textAt(lineSlot) } ?: return@key
                val inWindow = abs(lineSlot - slot) <= 1
                Box(
                    Modifier.fillMaxWidth().height(rowHeight)
                        .graphicsLayer {
                            val viewport = scroll?.value ?: slot.toFloat()
                            val travel = viewport - slot
                            val offset = if (abs(travel) <= MaxAnimatedLyricTravel) travel else 0f
                            val relative = lineSlot - slot - offset
                            val distance = abs(relative)
                            val emphasis = (1f - distance).coerceAtLeast(0f) * (animatedSinging?.value ?: singing)
                            val edgeFade = (2f - distance).coerceIn(0f, 1f)
                            translationY = (relative + 1f) * rowHeight.toPx()
                            transformOrigin = TransformOrigin(if (startIsLeft) 0f else 1f, 0.5f)
                            scaleX = 0.94f + 0.06f * emphasis
                            scaleY = scaleX
                            alpha = edgeFade * (contextAlpha + (1f - contextAlpha) * emphasis)
                        }
                        .then(if (inWindow) Modifier else Modifier.clearAndSetSemantics {})
                        .padding(start = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        line,
                        color = ink,
                        style = textStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
