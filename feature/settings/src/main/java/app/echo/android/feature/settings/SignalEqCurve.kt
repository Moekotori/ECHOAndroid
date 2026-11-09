package app.echo.android.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import app.echo.android.design.detectEchoTargetDrag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoEqResponsePoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

private const val EqMinHz = 20f
private const val EqMaxHz = 20_000f

/** Open instrument bed — no framed card. Signal layout uses rules + whitespace instead. */
@Composable
internal fun SignalEqWell(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), content = content)
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
    markerGainsDb: List<Float> = emptyList(),
    live: Boolean = true,
    showFrequencyLabels: Boolean = true,
    lockMarkerFrequency: Boolean = true,
    minGainDb: Float = -12f,
    maxGainDb: Float = 12f,
    onMarkerDrag: ((index: Int, frequencyHz: Float, gainDb: Float) -> Unit)? = null,
    selectedMarkerIndex: Int = -1,
    onMarkerSelected: ((Int) -> Unit)? = null,
    onMarkerDragFinished: (() -> Unit)? = null,
    markerSupportsGain: List<Boolean> = emptyList(),
    selectByFrequency: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val stroke = if (live) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.75f)
    val grid = scheme.outlineVariant
    val zero = scheme.onSurface.copy(alpha = 0.22f)
    val description = stringResource(R.string.eq_curve_reference)
    val peakMarker = if (selectByFrequency) abs(markerGainsDb.getOrElse(selectedMarkerIndex) { 0f })
    else markerGainsDb.maxOfOrNull { abs(it) } ?: 0f
    val peak = maxOf(peakMarker, points.maxOfOrNull { abs(it.gainDb) } ?: 0f)
    val autoRange = (ceil(peak / 6f) * 6f).coerceIn(12f, 48f)
    var heldRange by remember { mutableStateOf<Float?>(null) }
    val range = heldRange ?: autoRange
    val interactive = onMarkerDrag != null &&
        markerFrequenciesHz.isNotEmpty() &&
        markerGainsDb.size == markerFrequenciesHz.size
    val dragCallback = rememberUpdatedState(onMarkerDrag)
    val selectCallback = rememberUpdatedState(onMarkerSelected)
    val finishCallback = rememberUpdatedState(onMarkerDragFinished)
    val gainEditable = rememberUpdatedState(markerSupportsGain)
    val frequencies = rememberUpdatedState(markerFrequenciesHz)
    val gains = rememberUpdatedState(markerGainsDb)
    val selectedMarker = rememberUpdatedState(selectedMarkerIndex)
    var dragIndex by remember { mutableIntStateOf(-1) }
    var localFreq by remember { mutableFloatStateOf(0f) }
    var localGain by remember { mutableFloatStateOf(0f) }
    Box(modifier.background(scheme.surfaceContainerLow.copy(alpha = 0.65f), RoundedCornerShape(6.dp)).semantics { contentDescription = description }) {
        val bottomPad = if (showFrequencyLabels) 22.dp else 10.dp
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(start = 34.dp, end = 12.dp, top = 30.dp, bottom = bottomPad)
                .then(if (onMarkerSelected == null || markerGainsDb.size != markerFrequenciesHz.size) Modifier else Modifier.pointerInput(markerFrequenciesHz.size, range, selectByFrequency) {
                    detectTapGestures { position ->
                        if (selectByFrequency) {
                            frequencies.value.indices.minByOrNull { index ->
                                abs(position.x - eqLogX(frequencies.value[index].toFloat(), size.width.toFloat()))
                            }?.let { selectCallback.value?.invoke(it) }
                            return@detectTapGestures
                        }
                        val index = frequencies.value.indices.minByOrNull { index ->
                            (position - Offset(eqLogX(frequencies.value[index].toFloat(), size.width.toFloat()), eqGainY(gains.value[index], size.height.toFloat(), range))).getDistance()
                        }
                        if (index != null) {
                            val center = Offset(eqLogX(frequencies.value[index].toFloat(), size.width.toFloat()), eqGainY(gains.value[index], size.height.toFloat(), range))
                            if ((position - center).getDistance() <= 28.dp.toPx()) selectCallback.value?.invoke(index)
                        }
                    }
                })
                .then(
                    if (!interactive) {
                        Modifier
                    } else {
                        Modifier.pointerInput(
                            markerFrequenciesHz.size,
                            lockMarkerFrequency,
                            minGainDb,
                            maxGainDb,
                            range,
                            selectByFrequency,
                        ) {
                            val hit = 28.dp.toPx()
                            fun nearest(offset: Offset): Int {
                                var best = -1
                                var bestDist = hit
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                frequencies.value.forEachIndexed { index, frequencyHz ->
                                    if (selectByFrequency && index != selectedMarker.value) return@forEachIndexed
                                    val gain = if (dragIndex == index) localGain else gains.value[index]
                                    val freq = if (dragIndex == index) localFreq else frequencyHz.toFloat()
                                    val x = eqLogX(freq, width)
                                    val y = eqGainY(gain, height, range)
                                    val dist = (offset - Offset(x, y)).getDistance()
                                    if (dist <= bestDist) {
                                        bestDist = dist
                                        best = index
                                    }
                                }
                                return best
                            }
                            detectEchoTargetDrag(
                                targetAt = { nearest(it).takeIf { index -> index >= 0 } },
                                onStart = { index, _ ->
                                    heldRange = range
                                    dragIndex = index
                                    selectCallback.value?.invoke(index)
                                    localFreq = frequencies.value[index].toFloat()
                                    localGain = gains.value[index]
                                },
                                onStop = { finishCallback.value?.invoke(); dragIndex = -1; heldRange = null },
                                onCancel = { finishCallback.value?.invoke(); dragIndex = -1; heldRange = null },
                                onDrag = { change, _ ->
                                    val index = dragIndex
                                    change.consume()
                                    val width = size.width.toFloat()
                                    val height = size.height.toFloat()
                                    val nextFreq = if (lockMarkerFrequency) {
                                        frequencies.value[index].toFloat()
                                    } else {
                                        eqFreqFromX(change.position.x, width)
                                    }
                                    val nextGain = if (gainEditable.value.getOrNull(index) == false) gains.value[index] else snapEqGain(
                                        eqGainFromY(change.position.y, height, range)
                                            .coerceIn(minGainDb, maxGainDb),
                                    )
                                    localFreq = nextFreq
                                    localGain = nextGain
                                    dragCallback.value?.invoke(index, nextFreq, nextGain)
                                },
                            )
                        }
                    },
                ),
        ) {
            val zeroY = eqGainY(0f, size.height, range)
            for (line in 0..4) {
                val y = size.height * line / 4f
                val center = line == 2
                drawLine(
                    color = if (center) zero else grid.copy(alpha = 0.55f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
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
                            stroke.copy(alpha = if (live) 0.16f else 0.08f),
                            stroke.copy(alpha = 0.02f),
                        ),
                    ),
                )
                drawPath(
                    line,
                    stroke,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            if (markerGainsDb.size == markerFrequenciesHz.size) {
                markerFrequenciesHz.forEachIndexed { index, frequencyHz ->
                    val freq = if (dragIndex == index) localFreq else frequencyHz.toFloat()
                    val gain = if (dragIndex == index) localGain else markerGainsDb[index]
                    val center = Offset(eqLogX(freq, size.width), eqGainY(gain, size.height, range))
                    val selected = index == selectedMarkerIndex || index == dragIndex
                    if (selectByFrequency && !selected) return@forEachIndexed
                    if (selectByFrequency) drawLine(stroke.copy(alpha = 0.26f),
                        Offset(center.x, 0f), Offset(center.x, size.height), 1.dp.toPx())
                    if (selected) drawCircle(stroke.copy(alpha = 0.12f), 11.dp.toPx(), center)
                    drawCircle(scheme.surface, if (selected) 6.dp.toPx() else 4.dp.toPx(), center)
                    drawCircle(stroke, if (selected) 6.dp.toPx() else 4.dp.toPx(), center, style = Stroke(if (selected) 2.dp.toPx() else 1.5.dp.toPx()))
                    if (selected) drawCircle(stroke, 2.dp.toPx(), center)
                }
            }
        }
        Text("dB", Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 8.dp), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        Text(
            "+${range.toInt()}",
            modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 28.dp),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.86f),
        )
        Text("0", Modifier.align(Alignment.CenterStart).padding(start = 12.dp, top = 8.dp), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        Text("−${range.toInt()}", Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = bottomPad), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
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
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 34.dp, end = 12.dp, bottom = 4.dp),
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
    rangeDb: Float = 12f,
) {
    val scheme = MaterialTheme.colorScheme
    val stroke = if (active) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.70f)
    val range = rangeDb
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

internal fun eqGainFromY(y: Float, height: Float, range: Float): Float {
    if (height <= 0f || range <= 0f) return 0f
    return (range * (1f - 2f * y / height)).coerceIn(-range, range)
}

internal fun eqFreqFromX(x: Float, width: Float): Float {
    if (width <= 0f) return EqMinHz
    val min = ln(EqMinHz)
    val span = ln(EqMaxHz) - min
    val t = (x / width).coerceIn(0f, 1f)
    return exp(min + t * span).coerceIn(EqMinHz, EqMaxHz)
}
