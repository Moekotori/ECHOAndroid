package app.echo.android.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoTheme
import app.echo.android.model.playback.EchoEqResponsePoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToInt

private const val EqMinHz = 20f
private const val EqMaxHz = 20_000f

@Composable
internal fun SignalEqWell(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val theme = echoTheme()
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .clip(shape)
            .background(
                if (theme.dark) {
                    Brush.verticalGradient(listOf(theme.ink.copy(alpha = 0.96f), theme.night))
                } else {
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.96f), scheme.surfaceVariant.copy(alpha = 0.42f)),
                    )
                },
            )
            .border(
                width = 1.dp,
                color = if (theme.dark) theme.glassBorder else scheme.outlineVariant.copy(alpha = 0.70f),
                shape = shape,
            ),
        content = content,
    )
}

@Composable
internal fun SignalEqCurve(
    points: List<EchoEqResponsePoint>,
    modifier: Modifier = Modifier,
    markerFrequenciesHz: List<Int> = emptyList(),
    live: Boolean = true,
    showFrequencyLabels: Boolean = true,
) {
    SignalEqPlot(
        points = points,
        modifier = modifier.fillMaxWidth().height(168.dp),
        markerFrequenciesHz = markerFrequenciesHz,
        live = live,
        showFrequencyLabels = showFrequencyLabels,
    )
}

@Composable
internal fun SignalEqPlot(
    points: List<EchoEqResponsePoint>,
    modifier: Modifier = Modifier,
    markerFrequenciesHz: List<Int> = emptyList(),
    live: Boolean = true,
    showFrequencyLabels: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val theme = echoTheme()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val stroke = if (live) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.55f)
    val grid = scheme.outlineVariant
    val zero = scheme.onSurface.copy(alpha = 0.42f)
    val description = stringResource(R.string.eq_curve_reference)
    val range = maxOf(12f, ceil((points.maxOfOrNull { abs(it.gainDb) } ?: 0f) / 6f) * 6f).coerceAtMost(48f)
    Box(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 22.dp, bottom = if (showFrequencyLabels) 22.dp else 10.dp)) {
            val zeroY = eqGainY(0f, size.height, range)
            if (!lightweight && live) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(stroke.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.42f, 0f),
                        radius = size.width * 0.72f,
                    ),
                )
            }
            for (line in 0..4) {
                val y = size.height * line / 4f
                val center = line == 2
                drawLine(
                    color = if (center) zero else grid.copy(alpha = if (theme.dark) 0.55f else 0.80f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (center) 1.6.dp.toPx() else 1.dp.toPx(),
                )
            }
            listOf(EqMinHz, 200f, 2_000f, EqMaxHz).forEach { frequencyHz ->
                val x = eqLogX(frequencyHz, size.width)
                drawLine(grid.copy(alpha = 0.32f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
            markerFrequenciesHz.forEach { frequencyHz ->
                val x = eqLogX(frequencyHz.toFloat(), size.width)
                drawLine(stroke.copy(alpha = 0.22f), Offset(x, size.height - 8.dp.toPx()), Offset(x, size.height), 2.dp.toPx())
            }
            if (points.size > 1) {
                val line = Path()
                val area = Path()
                points.forEachIndexed { index, point ->
                    val x = eqLogX(point.frequencyHz, size.width)
                    val y = eqGainY(point.gainDb, size.height, range)
                    if (index == 0) {
                        line.moveTo(x, y)
                        area.moveTo(x, y)
                    } else {
                        line.lineTo(x, y)
                        area.lineTo(x, y)
                    }
                }
                area.lineTo(eqLogX(points.last().frequencyHz, size.width), zeroY)
                area.lineTo(eqLogX(points.first().frequencyHz, size.width), zeroY)
                area.close()
                drawPath(
                    area,
                    Brush.verticalGradient(
                        colors = listOf(
                            stroke.copy(alpha = if (live) 0.40f else 0.16f),
                            stroke.copy(alpha = if (live) 0.08f else 0.03f),
                        ),
                    ),
                )
                if (!lightweight && live) {
                    drawPath(
                        line,
                        stroke.copy(alpha = 0.24f),
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                drawPath(
                    line,
                    stroke,
                    style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        Text(
            "+${range.toInt()} dB",
            modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.86f),
        )
        Text(
            description,
            modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.86f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showFrequencyLabels) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("20 Hz", "200 Hz", "2 kHz", "20 kHz").forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant.copy(alpha = 0.86f))
                }
            }
        }
    }
}

@Composable
internal fun SignalEqSparkline(
    gainsDb: List<Float>,
    frequenciesHz: List<Int>,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val stroke = if (active) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.70f)
    val range = 12f
    Canvas(modifier.fillMaxWidth().height(42.dp)) {
        if (gainsDb.isEmpty() || frequenciesHz.isEmpty()) return@Canvas
        val zeroY = eqGainY(0f, size.height, range)
        drawLine(scheme.outlineVariant.copy(alpha = 0.55f), Offset(0f, zeroY), Offset(size.width, zeroY), 1.dp.toPx())
        val line = Path()
        val area = Path()
        val last = minOf(gainsDb.size, frequenciesHz.size) - 1
        for (index in 0..last) {
            val x = eqLogX(frequenciesHz[index].toFloat(), size.width)
            val y = eqGainY(gainsDb[index], size.height, range)
            if (index == 0) {
                line.moveTo(x, y)
                area.moveTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(eqLogX(frequenciesHz[last].toFloat(), size.width), zeroY)
        area.lineTo(eqLogX(frequenciesHz[0].toFloat(), size.width), zeroY)
        area.close()
        drawPath(area, stroke.copy(alpha = if (active) 0.34f else 0.14f))
        drawPath(line, stroke, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
internal fun SignalEqFader(
    frequencyHz: Int,
    gainDb: Float,
    minGainDb: Float,
    maxGainDb: Float,
    enabled: Boolean,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val theme = echoTheme()
    val frequency = formatEqFrequency(frequencyHz)
    val slot = if (theme.dark) Color.Black.copy(alpha = 0.38f) else scheme.outlineVariant.copy(alpha = 0.70f)
    val active = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.38f)
    var dragging by remember { mutableStateOf(false) }
    var localGain by remember { mutableFloatStateOf(gainDb) }
    LaunchedEffect(gainDb) { if (!dragging) localGain = gainDb }
    val display = localGain.coerceIn(minGainDb, maxGainDb)
    Column(
        modifier
            .semantics(mergeDescendants = true) {
                contentDescription = frequency
                if (!enabled) disabled()
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = eqUnitValue(display, minGainDb, maxGainDb),
                    range = 0f..1f,
                )
                setProgress {
                    if (!enabled) return@setProgress false
                    onGainChange(snapEqGain(eqValueFromUnit(it, minGainDb, maxGainDb)))
                    true
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            formatEqGain(display),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = active,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(176.dp)
                .pointerInput(enabled, minGainDb, maxGainDb) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (!enabled) return@awaitEachGesture
                        // Consume immediately so the parent Signal page does not steal the vertical drag.
                        down.consume()
                        dragging = true
                        val inset = 5.dp.toPx()
                        val next = snapEqGain(eqLinearValue(down.position.y, size.height.toFloat(), maxGainDb, minGainDb, inset))
                        localGain = next
                        onGainChange(next)
                        drag(down.id) { change ->
                            change.consume()
                            val dragged = snapEqGain(eqLinearValue(change.position.y, size.height.toFloat(), maxGainDb, minGainDb, inset))
                            localGain = dragged
                            onGainChange(dragged)
                        }
                        dragging = false
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                val wellWidth = 20.dp.toPx()
                val slotWidth = 8.dp.toPx()
                val fillWidth = 5.dp.toPx()
                val thumbW = 22.dp.toPx()
                val thumbH = 10.dp.toPx()
                val inset = thumbH / 2f
                val x = size.width / 2f
                val zeroY = eqLinearPosition(0f, size.height, maxGainDb, minGainDb, inset)
                val gainY = eqLinearPosition(display, size.height, maxGainDb, minGainDb, inset)
                drawRoundRect(
                    color = if (theme.dark) Color.Black.copy(alpha = 0.46f) else slot,
                    topLeft = Offset(x - wellWidth / 2f, 0f),
                    size = Size(wellWidth, size.height),
                    cornerRadius = CornerRadius(wellWidth / 2f),
                )
                drawRoundRect(
                    color = slot.copy(alpha = if (theme.dark) 0.85f else 1f),
                    topLeft = Offset(x - slotWidth / 2f, 4.dp.toPx()),
                    size = Size(slotWidth, size.height - 8.dp.toPx()),
                    cornerRadius = CornerRadius(slotWidth / 2f),
                )
                val fillTop = minOf(zeroY, gainY)
                val fillHeight = abs(zeroY - gainY).coerceAtLeast(fillWidth)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(active.copy(alpha = 0.98f), active.copy(alpha = 0.52f)),
                    ),
                    topLeft = Offset(x - fillWidth / 2f, fillTop),
                    size = Size(fillWidth, fillHeight),
                    cornerRadius = CornerRadius(fillWidth / 2f),
                )
                drawLine(
                    color = scheme.onSurface.copy(alpha = 0.38f),
                    start = Offset(x - 11.dp.toPx(), zeroY),
                    end = Offset(x + 11.dp.toPx(), zeroY),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawRoundRect(
                    color = active,
                    topLeft = Offset(x - thumbW / 2f, gainY - thumbH / 2f),
                    size = Size(thumbW, thumbH),
                    cornerRadius = CornerRadius(thumbH / 2f),
                )
                drawLine(
                    color = Color.White.copy(alpha = if (theme.dark) 0.40f else 0.62f),
                    start = Offset(x - thumbW / 2f + 5.dp.toPx(), gainY),
                    end = Offset(x + thumbW / 2f - 5.dp.toPx(), gainY),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Text(
            frequency,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SignalGainStrip(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    contentDescription: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    snap: (Float) -> Float = ::snapEqGain,
) {
    val scheme = MaterialTheme.colorScheme
    val theme = echoTheme()
    val track = if (theme.dark) Color.Black.copy(alpha = 0.32f) else scheme.outlineVariant.copy(alpha = 0.70f)
    val active = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.38f)
    var dragging by remember { mutableStateOf(false) }
    var localValue by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { if (!dragging) localValue = value }
    val display = localValue.coerceIn(valueRange.start, valueRange.endInclusive)
    val min = valueRange.start
    val max = valueRange.endInclusive
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                if (!enabled) disabled()
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = eqUnitValue(display, min, max),
                    range = 0f..1f,
                )
                setProgress {
                    if (!enabled) return@setProgress false
                    onValueChange(snap(eqValueFromUnit(it, min, max)))
                    true
                }
            }
            .pointerInput(enabled, min, max) {
                detectTapGestures { offset ->
                    if (!enabled) return@detectTapGestures
                    val next = snap(eqLinearValue(offset.x, size.width.toFloat(), min, max, 9.dp.toPx()))
                    localValue = next
                    onValueChange(next)
                }
            }
            .pointerInput(enabled, min, max) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (!enabled) return@detectHorizontalDragGestures
                        dragging = true
                        val next = snap(eqLinearValue(offset.x, size.width.toFloat(), min, max, 9.dp.toPx()))
                        localValue = next
                        onValueChange(next)
                    },
                    onHorizontalDrag = { change, _ ->
                        if (!enabled) return@detectHorizontalDragGestures
                        change.consume()
                        val next = snap(eqLinearValue(change.position.x, size.width.toFloat(), min, max, 9.dp.toPx()))
                        localValue = next
                        onValueChange(next)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val trackHeight = 8.dp.toPx()
            val top = (size.height - trackHeight) / 2f
            val thumbW = 20.dp.toPx()
            val thumbH = 12.dp.toPx()
            val inset = thumbW / 2f
            val x = eqLinearPosition(display, size.width, min, max, inset)
            val zeroX = eqLinearPosition(0f, size.width, min, max, inset)
            drawRoundRect(
                color = track,
                topLeft = Offset(0f, top),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
            val fillLeft = minOf(zeroX, x)
            val fillWidth = abs(zeroX - x).coerceAtLeast(trackHeight)
            drawRoundRect(
                color = active.copy(alpha = 0.78f),
                topLeft = Offset(fillLeft, top),
                size = Size(fillWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
            drawLine(
                color = scheme.onSurface.copy(alpha = 0.32f),
                start = Offset(zeroX, top - 3.dp.toPx()),
                end = Offset(zeroX, top + trackHeight + 3.dp.toPx()),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawRoundRect(
                color = active,
                topLeft = Offset(x - thumbW / 2f, (size.height - thumbH) / 2f),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbH / 2f),
            )
        }
    }
}

internal fun snapEqGain(gainDb: Float): Float = (gainDb * 10f).roundToInt() / 10f

internal fun eqLogX(frequencyHz: Float, width: Float): Float {
    if (width <= 0f) return 0f
    val min = ln(EqMinHz)
    val span = ln(EqMaxHz) - min
    val freq = frequencyHz.coerceIn(EqMinHz, EqMaxHz)
    return ((ln(freq) - min) / span * width).coerceIn(0f, width)
}

internal fun eqGainY(gainDb: Float, height: Float, range: Float): Float {
    if (height <= 0f || range <= 0f) return 0f
    return height * (1f - gainDb.coerceIn(-range, range) / range) / 2f
}

internal fun eqLinearValue(position: Float, size: Float, start: Float, end: Float, inset: Float = 0f): Float {
    val inner = size - 2f * inset
    if (inner <= 0f) return start
    val t = ((position - inset) / inner).coerceIn(0f, 1f)
    return start + t * (end - start)
}

internal fun eqLinearPosition(value: Float, size: Float, start: Float, end: Float, inset: Float = 0f): Float {
    val inner = size - 2f * inset
    val span = end - start
    if (inner <= 0f || span == 0f) return inset
    return inset + ((value - start) / span * inner).coerceIn(0f, inner)
}

private fun eqUnitValue(value: Float, min: Float, max: Float): Float {
    val span = max - min
    if (span == 0f) return 0f
    return ((value - min) / span).coerceIn(0f, 1f)
}

private fun eqValueFromUnit(unit: Float, min: Float, max: Float): Float =
    min + unit.coerceIn(0f, 1f) * (max - min)
