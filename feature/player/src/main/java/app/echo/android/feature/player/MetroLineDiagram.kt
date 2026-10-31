package app.echo.android.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction

private const val StationCount = 5

/** Fixed time stops, not inferred musical sections. Only the train reads every position tick. */
@Composable
internal fun MetroLineDiagram(
    position: State<Long>,
    duration: State<Long>,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reached by remember(position, duration) {
        derivedStateOf { (progressFraction(position.value, duration.value) * (StationCount - 1)).toInt() }
    }
    val total = duration.value
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.player_style_metro_route), color = MetroStyle.Muted,
                fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(6.dp).background(if (playing) MetroStyle.Line else MetroStyle.Rail, CircleShape))
                Text(stringResource(if (playing) R.string.radio_state_playing else R.string.radio_state_paused),
                    color = MetroStyle.Ink, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        val density = LocalDensity.current
        val dash = remember(density) { with(density) { PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) } }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // Short landscape panes use a horizontal route instead of overflowing five vertical rows.
            val horizontal = maxHeight < 230.dp
            if (horizontal) {
                CompactMetroRoute(position, duration, reached, onSeek)
            } else {
                val top = 26.dp
                val span = maxHeight - 52.dp
                Canvas(Modifier.fillMaxSize()) {
                    val x = 27.dp.toPx()
                    val start = top.toPx()
                    val length = span.toPx()
                    val fraction = progressFraction(position.value, duration.value)
                    val progressY = start + length * fraction
                    val lit = Offset(x - 1.5.dp.toPx(), start)
                    drawLine(MetroStyle.Line.copy(alpha = 0.08f), Offset(x, start), Offset(x, progressY), 17.dp.toPx(), StrokeCap.Round)
                    drawLine(MetroStyle.Rail, Offset(x, start), Offset(x, start + length), 8.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(lit.x, start), Offset(lit.x, start + length), 1.5.dp.toPx(), StrokeCap.Round)
                    drawLine(MetroStyle.Line, Offset(x, start), Offset(x, progressY), 8.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.28f), Offset(lit.x, start), Offset(lit.x, progressY), 1.5.dp.toPx(), StrokeCap.Round)
                    // Direction chevrons on the part of the line the train has not covered yet.
                    for (k in 0 until StationCount - 1) {
                        for (n in 1..2) {
                            val cy = start + length * (k + n / 3f) / (StationCount - 1)
                            if (cy > progressY + 22.dp.toPx()) drawRouteChevron(x, cy)
                        }
                    }
                    val gap = length / (StationCount - 1)
                    for (k in 0 until StationCount) {
                        val y = start + gap * k
                        val current = k == reached
                        drawLine(if (current) MetroStyle.Line else MetroStyle.Rule, Offset(x + 13.dp.toPx(), y),
                            Offset(52.dp.toPx(), y), 1.5.dp.toPx(), StrokeCap.Round)
                        drawMetroStation(Offset(x, y), k <= reached, current, k == 0 || k == StationCount - 1)
                        if (k < StationCount - 1) {
                            val dividerY = y + gap / 2
                            drawLine(MetroStyle.Rule, Offset(60.dp.toPx(), dividerY), Offset(size.width, dividerY), 1.dp.toPx(), pathEffect = dash)
                        }
                    }
                    drawMetroTrain(Offset(x, progressY))
                }
                for (k in 0 until StationCount) {
                    val time = stationTime(total, k)
                    val current = k == reached
                    Row(
                        Modifier.offset(y = top + span * k / (StationCount - 1) - 22.dp)
                            .fillMaxWidth().height(44.dp).padding(start = 52.dp)
                            .then(if (current) Modifier.shadow(3.dp, RoundedCornerShape(8.dp),
                                ambientColor = MetroStyle.Line.copy(alpha = 0.3f), spotColor = MetroStyle.Line.copy(alpha = 0.3f)) else Modifier)
                            .background(if (current) MetroStyle.Board else Color.Transparent, RoundedCornerShape(8.dp))
                            .then(if (current) Modifier.border(1.dp, MetroStyle.Line.copy(alpha = 0.16f), RoundedCornerShape(8.dp)) else Modifier)
                            .drawBehind {
                                if (current) {
                                    drawRoundRect(MetroStyle.Line, Offset(0f, 10.dp.toPx()), Size(3.dp.toPx(), size.height - 20.dp.toPx()),
                                        CornerRadius(1.5.dp.toPx()))
                                }
                            }
                            .clickable(enabled = total > 0, role = Role.Button,
                                onClickLabel = stringResource(R.string.player_style_metro_seek_to, formatDuration(time))) { onSeek(time) }
                            .semantics { selected = current }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stationCode(k), Modifier.background(if (current) MetroStyle.Line else Color.Transparent,
                            RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 3.dp),
                            color = if (current) Color.White else if (k <= reached) MetroStyle.Line else MetroStyle.Muted,
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
                        Text(stationLabel(k), Modifier.weight(1f),
                            color = if (k <= reached) MetroStyle.Ink else MetroStyle.Muted,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                            fontSize = if (current) 17.sp else 14.sp, lineHeight = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatDuration(time), color = if (current) MetroStyle.Line else MetroStyle.Muted,
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (total <= 0) "—"
                else if (reached == StationCount - 1) stringResource(R.string.player_style_metro_arrived)
                else stringResource(R.string.player_style_metro_next, stationLabel(reached + 1)),
                Modifier.weight(1f), color = MetroStyle.Ink, fontSize = 10.sp, lineHeight = 14.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            MetroArrivalTime(position, duration, reached)
        }
    }
}

@Composable
private fun MetroArrivalTime(position: State<Long>, duration: State<Long>, reached: Int) {
    val seconds by remember(position, duration, reached) {
        derivedStateOf {
            val next = stationTime(duration.value, (reached + 1).coerceAtMost(StationCount - 1))
            ((next - position.value).coerceAtLeast(0L) + 999L) / 1000L
        }
    }
    Text(if (duration.value <= 0 || reached == StationCount - 1) "—"
        else stringResource(R.string.player_style_metro_arrival_in, formatDuration(seconds * 1000L)),
        Modifier.background(MetroStyle.Board, RoundedCornerShape(10.dp))
            .border(0.7.dp, MetroStyle.Rule, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        color = MetroStyle.Ink, fontSize = 10.sp, lineHeight = 14.sp, fontFamily = FontFamily.Monospace)
}

@Composable
private fun CompactMetroRoute(position: State<Long>, duration: State<Long>, reached: Int, onSeek: (Long) -> Unit) {
    val total = duration.value
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val routeWidth = maxOf(maxWidth, 240.dp)
        val step = routeWidth / StationCount
        val start = step / 2
        val span = routeWidth - step
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            Box(Modifier.width(routeWidth).fillMaxHeight()) {
                Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                    val y = 20.dp.toPx()
                    val x = start.toPx()
                    val length = span.toPx()
                    val fraction = progressFraction(position.value, duration.value)
                    drawLine(MetroStyle.Rail, Offset(x, y), Offset(x + length, y), 7.dp.toPx(), StrokeCap.Round)
                    drawLine(MetroStyle.Line, Offset(x, y), Offset(x + length * fraction, y), 7.dp.toPx(), StrokeCap.Round)
                    for (k in 0 until StationCount) drawMetroStation(Offset(x + length * k / (StationCount - 1), y),
                        k <= reached, k == reached, k == 0 || k == StationCount - 1)
                    drawMetroTrain(Offset(x + length * fraction, y))
                }
                Row(Modifier.fillMaxWidth()) {
                    for (k in 0 until StationCount) {
                        val time = stationTime(total, k)
                        Column(Modifier.weight(1f).heightIn(min = 74.dp)
                            .clickable(enabled = total > 0, role = Role.Button,
                                onClickLabel = stringResource(R.string.player_style_metro_seek_to, formatDuration(time))) { onSeek(time) }
                            .semantics { selected = k == reached }.padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stationCode(k), color = MetroStyle.Ink, fontSize = 10.sp, lineHeight = 14.sp, fontFamily = FontFamily.Monospace,
                                fontWeight = if (k == reached) FontWeight.Bold else FontWeight.Normal)
                            Text(formatDuration(time), color = MetroStyle.Muted, fontSize = 9.sp, lineHeight = 13.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

private fun stationTime(duration: Long, index: Int): Long = (duration.coerceAtLeast(0) * (index / (StationCount - 1.0))).toLong()
private fun stationCode(index: Int): String = "04·0${index + 1}"

@Composable
private fun stationLabel(index: Int): String = when (index) {
    0 -> stringResource(R.string.player_style_metro_origin)
    StationCount - 1 -> stringResource(R.string.player_style_metro_terminus)
    else -> stringResource(R.string.player_style_metro_station, index + 1)
}

/** Station marker: white disc with a thick ring; the current stop gets a halo, the terminals an inner dot. */
private fun DrawScope.drawMetroStation(center: Offset, passed: Boolean, current: Boolean, terminal: Boolean) {
    val radius = (if (terminal) 10.dp else 8.dp).toPx()
    val tone = if (passed) MetroStyle.Line else MetroStyle.Rail
    if (current) drawCircle(MetroStyle.Line.copy(alpha = 0.16f), radius + 6.dp.toPx(), center)
    drawCircle(Color.Black.copy(alpha = 0.08f), radius + 1.dp.toPx(), center + Offset(0f, 1.dp.toPx()))
    if (passed && !current) {
        // Already served stop: solid disc with a check mark.
        drawCircle(MetroStyle.Line, radius, center)
        val u = radius / 8f
        val white = Color.White
        drawLine(white, center + Offset(-3.2f * u, 0.2f * u), center + Offset(-1f * u, 2.6f * u), 1.8.dp.toPx(), StrokeCap.Round)
        drawLine(white, center + Offset(-1f * u, 2.6f * u), center + Offset(3.4f * u, -2.4f * u), 1.8.dp.toPx(), StrokeCap.Round)
        return
    }
    drawCircle(Color.White, radius, center)
    drawCircle(tone, radius - 1.5.dp.toPx(), center, style = Stroke(3.dp.toPx()))
    if (terminal || current) drawCircle(tone, (if (terminal) 4 else 3).dp.toPx(), center)
}

private fun DrawScope.drawRouteChevron(x: Float, y: Float) {
    val half = 3.dp.toPx()
    val rise = 2.dp.toPx()
    val white = Color.White.copy(alpha = 0.9f)
    drawLine(white, Offset(x - half, y - rise), Offset(x, y + rise), 1.6.dp.toPx(), StrokeCap.Round)
    drawLine(white, Offset(x, y + rise), Offset(x + half, y - rise), 1.6.dp.toPx(), StrokeCap.Round)
}

/** A small front-facing carriage, with a windscreen and headlights. No independent animation clock. */
private fun DrawScope.drawMetroTrain(center: Offset) {
    val w = 11.dp.toPx()
    val h = 14.dp.toPx()
    drawRoundRect(MetroStyle.Ink.copy(alpha = 0.12f), center - Offset(w + 2.dp.toPx(), h - 1.dp.toPx()),
        Size(w * 2 + 4.dp.toPx(), h * 2 + 4.dp.toPx()), CornerRadius(6.dp.toPx()))
    drawRoundRect(MetroStyle.Paper, center - Offset(w + 3.dp.toPx(), h + 3.dp.toPx()),
        Size(w * 2 + 6.dp.toPx(), h * 2 + 6.dp.toPx()), CornerRadius(6.dp.toPx()))
    drawRoundRect(MetroStyle.Line, center - Offset(w, h), Size(w * 2, h * 2), CornerRadius(4.dp.toPx()))
    drawRoundRect(MetroStyle.Sign, center - Offset(7.dp.toPx(), 8.dp.toPx()), Size(14.dp.toPx(), 10.dp.toPx()), CornerRadius(2.dp.toPx()))
    drawLine(Color.White.copy(alpha = 0.45f), center - Offset(4.dp.toPx(), 6.dp.toPx()),
        center - Offset(1.dp.toPx(), 6.dp.toPx()), 1.dp.toPx(), StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.22f), center + Offset(-w + 3.dp.toPx(), 3.dp.toPx()),
        center + Offset(w - 3.dp.toPx(), 3.dp.toPx()), 1.dp.toPx(), StrokeCap.Round)
    drawCircle(MetroStyle.Amber.copy(alpha = 0.35f), 3.5.dp.toPx(), center + Offset(-6.dp.toPx(), 7.dp.toPx()))
    drawCircle(MetroStyle.Amber.copy(alpha = 0.35f), 3.5.dp.toPx(), center + Offset(6.dp.toPx(), 7.dp.toPx()))
    drawCircle(MetroStyle.Amber, 1.5.dp.toPx(), center + Offset(-6.dp.toPx(), 7.dp.toPx()))
    drawCircle(MetroStyle.Amber, 1.5.dp.toPx(), center + Offset(6.dp.toPx(), 7.dp.toPx()))
    drawLine(MetroStyle.Sign, center + Offset(-5.dp.toPx(), 13.dp.toPx()),
        center + Offset(5.dp.toPx(), 13.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
}
