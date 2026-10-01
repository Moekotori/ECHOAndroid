package app.echo.android.feature.player.afterglow

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.settings.EchoLyricsPageStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private class AfterglowPlanResult(val source: EchoLyrics, val durationMs: Long, val plan: AfterglowPlan?)

@Composable
internal fun AfterglowStage(
    lyrics: EchoLyrics, style: EchoLyricsPageStyle, trackKey: String?, title: String,
    hostPosition: State<Long>, durationMs: Long, playing: Boolean, speed: Float, visible: Boolean,
    fontFamily: FontFamily?, fontScale: Float, lineSpacing: Float, motionMode: String,
    showTranslation: Boolean, showRomanization: Boolean, wordHighlight: Boolean,
    estimatedWordHighlight: Boolean, highlightIntensity: Float,
    onSeek: (Long) -> Unit, modifier: Modifier = Modifier, fallback: @Composable () -> Unit,
    controlsLayer: (@Composable (@Composable () -> Unit) -> Unit)? = null,
    onControlsInteraction: () -> Unit = {},
    onControlsMenu: (Boolean) -> Unit = {},
    onStagePalette: (AfterglowPalette) -> Unit = {},
) {
    val budget = AfterglowBudget.forMode(LocalEchoEffectivePerformanceMode.current)
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val renderActive = visible && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val motion = budget.glyphMotion && motionMode != "calm"
    val result by produceState<AfterglowPlanResult?>(null, lyrics, durationMs) {
        value = null
        value = AfterglowPlanResult(lyrics, durationMs, withContext(Dispatchers.Default) {
            AfterglowPlan.build(lyrics, durationMs) { ensureActive() }
        })
    }
    val currentResult = result?.takeIf { it.source === lyrics && it.durationMs == durationMs }
    if (currentResult != null && currentResult.plan == null) {
        fallback()
        return
    }
    val plan = currentResult?.plan
    var textFits by remember(lyrics) { mutableStateOf(true) }
    var timedInk by remember(lyrics) { mutableStateOf(false) }
    var seed by rememberSaveable(trackKey) { mutableIntStateOf(trackKey?.hashCode() ?: 20260930) }
    var variant by rememberSaveable(style) { mutableIntStateOf(0) }
    var chosenScene by rememberSaveable(style) { mutableStateOf<String?>(null) }
    val palette = AfterglowPalette.forStyle(style, variant)
    LaunchedEffect(palette) { onStagePalette(palette) }
    val clock = rememberAfterglowPosition(hostPosition, trackKey, playing, speed, visible,
        if (plan != null && textFits && (motion || wordHighlight && budget.glyphMotion && timedInk)) budget.framesPerSecond else 0)
    val index by remember(plan, clock, lyrics.offsetMs) {
        derivedStateOf(structuralEqualityPolicy()) { plan?.indexAt(clock.value + lyrics.offsetMs) ?: -1 }
    }
    val currentPlan = plan
    val line = currentPlan?.lines?.getOrNull(index)
    val composition = remember(index, seed) { afterglowComposition(index.coerceAtLeast(0), seed) }
    val scene = AfterglowScene.forStyle(style).firstOrNull { it.pcId == chosenScene } ?: AfterglowScene.automatic(style, index)
    val sceneSeed = remember(index / 2, seed, chosenScene) { afterglowComposition(if (chosenScene == null) index.coerceAtLeast(0) / 2 else 0, seed) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val typeface = LocalFontFamilyResolver.current.resolve(fontFamily ?: FontFamily.Default, FontWeight.Normal).value as Typeface
    val textSize = with(density) { (34f * fontScale.coerceIn(0.50f, 1.28f)).sp.toPx() }
    val captionSize = with(density) { (14f * fontScale.coerceIn(0.50f, 1.28f)).sp.toPx() }
    val endMs = if (index >= 0 && currentPlan != null) currentPlan.endAt(index) else 0L
    val textLayout by produceState<AfterglowTextLayout?>(null, line, endMs, bounds, typeface,
        textSize, captionSize, lineSpacing, showTranslation, showRomanization, estimatedWordHighlight, wordHighlight, budget.glyphMotion, renderActive, composition) {
        value = null
        if (renderActive && line != null && bounds.width > 0 && bounds.height > 0) {
            value = withContext(Dispatchers.Default) {
                AfterglowTextLayout.build(line, endMs, (bounds.width * 0.86f).toInt(),
                    (bounds.height * 0.65f).toInt(), typeface, textSize, captionSize, lineSpacing,
                    showTranslation, showRomanization, estimatedWordHighlight && wordHighlight && budget.glyphMotion,
                    composition, budget.framesPerSecond == 0)
            }
        }
    }
    LaunchedEffect(textLayout, line) {
        val current = textLayout?.takeIf { it.line === line }
        textFits = current?.fits != false
        timedInk = current?.glyphs?.any { it.source.startMs != null } == true
    }
    val animatedInk = wordHighlight && budget.glyphMotion && timedInk
    val drawPosition by remember(clock, line, endMs, lyrics.offsetMs, motion, animatedInk) {
        derivedStateOf(structuralEqualityPolicy()) {
            val position = (clock.value + lyrics.offsetMs).coerceAtLeast(0L)
            // A static / lightweight scene changes only at a lyric or gap boundary, not at every host tick.
            if (motion || animatedInk) position
            else if (line == null) 0L else if (position < endMs) line.startMs else endMs
        }
    }
    // No content is truncated to meet a visual budget; large paragraphs fall back to the existing list.
    if (textLayout?.fits == false) {
        fallback()
        return
    }
    val preparer = remember { AfterglowBackdropPreparer() }
    val backdrop by produceState<AfterglowBackdrop?>(null, bounds, palette, scene, sceneSeed, budget, renderActive) {
        if (!renderActive) { value = null; return@produceState }
        // Hold the current scene while its replacement is built; never blank the stage at a cut.
        if (bounds.width > 0 && bounds.height > 0) value = preparer.prepare(bounds.width, bounds.height, palette, scene, sceneSeed, budget)
    }
    Box(modifier) {
        Box(Modifier.fillMaxSize().onSizeChanged { bounds = it }) {
            // Only this draw is invalidated by the clock. Native paints / paths are reused.
            // Own a display list so unrelated toolbar ripples do not replay the lyric painter.
            Canvas(Modifier.fillMaxSize().graphicsLayer().semantics {
                contentDescription = if (line == null) title else buildString {
                    append(line.text)
                    if (showRomanization) line.romanization?.let { append('\n'); append(it) }
                    if (showTranslation) line.translation?.let { append('\n'); append(it) }
                }
            }) {
                val position = drawPosition
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.clipRect(0f, 0f, size.width, size.height)
                val prepared = backdrop
                if (prepared != null) {
                    canvas.save()
                    canvas.scale(size.width / prepared.width, size.height / prepared.height)
                    prepared.draw(canvas, position / 1000f, motion)
                    canvas.restore()
                } else canvas.drawColor(palette.background)
                textLayout?.takeIf { it.line === line }?.let { layout ->
                    drawAfterglowText(canvas, layout, prepared?.palette ?: palette, position, endMs, composition,
                        motion, wordHighlight && budget.glyphMotion, highlightIntensity, size.width, size.height)
                }
                canvas.restore()
            }
            if (line == null) {
                Text(title, color = Color(palette.foreground), fontFamily = fontFamily,
                    style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp))
            }
        }
        val sceneControls: @Composable () -> Unit = {
            AfterglowControls(index, currentPlan, lyrics.offsetMs, style, scene, chosenScene,
                onScene = { chosenScene = it }, onPalette = { variant = (variant + 1) % 3 },
                onRandomize = { seed = seed * 1664525 + 1013904223 }, onSeek = onSeek,
                onInteraction = onControlsInteraction, onMenu = onControlsMenu)
        }
        if (controlsLayer != null) controlsLayer(sceneControls)
        else Box(Modifier.align(Alignment.BottomCenter).padding(20.dp)) { sceneControls() }
    }
}
