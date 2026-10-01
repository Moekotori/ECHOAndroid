package app.echo.android.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.drawEchoControlRail
import app.echo.android.design.drawEchoControlThumb
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.model.radio.EchoRadioStation

/** High-frequency clock reads stay here, separate from artwork and title layout. */
@Composable
internal fun ExpressiveProgress(pixel: Boolean, trackKey: String?, position: State<Long>, duration: State<Long>, onSeek: (Long) -> Unit) {
    val ink = if (pixel) ExpressivePlayerStyle.PixelInk else ExpressivePlayerStyle.PosterInk
    if (EchoRadioStation.isRadio(trackKey)) {
        Text(stringResource(R.string.radio_live), color = ink)
        return
    }
    val total = duration.value
    var preview by remember(trackKey, total) { mutableStateOf<Float?>(null) }
    val fraction = preview ?: progressFraction(position.value, total)
    val elapsed = if (total > 0) (fraction * total).toLong() else position.value
    val seek: @Composable (Modifier) -> Unit = { modifier ->
        ExpressiveSeekBar(pixel, fraction, preview != null, total > 0, trackKey,
            { preview = it }, { if (total > 0) onSeek((it * total).toLong()); preview = null },
            { preview = null }, modifier)
    }
    if (pixel) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatDuration(elapsed).padStart(5, '0'), color = ink, fontFamily = ExpressivePlayerStyle.PixelBody, fontSize = 22.sp)
            seek(Modifier.weight(1f))
            Text(formatDuration(total).padStart(5, '0'), color = ink, fontFamily = ExpressivePlayerStyle.PixelBody, fontSize = 20.sp)
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().height(116.dp), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(formatDuration(elapsed).padStart(5, '0'),
                    style = TextStyle(color = ink, fontFamily = RecordSleeveStyle.BodyFont, letterSpacing = (-4).sp),
                    autoSize = TextAutoSize.StepBased(40.sp, 100.sp, 1.sp), maxLines = 1,
                    modifier = Modifier.weight(1f).fillMaxHeight())
                Text(formatDuration(total).padStart(5, '0'), color = ink, fontFamily = RecordSleeveStyle.BodyFont,
                    fontSize = 22.sp, modifier = Modifier.padding(bottom = 14.dp))
            }
            seek(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ExpressiveSeekBar(pixel: Boolean, fraction: Float, engaged: Boolean, enabled: Boolean, key: String?,
                              onPreview: (Float) -> Unit, onCommit: (Float) -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    val haptics by rememberUpdatedState(rememberEchoHapticPerformer())
    val preview by rememberUpdatedState<(Float) -> Unit>({ onPreview(it); haptics.seek(it) })
    val commit by rememberUpdatedState<(Float) -> Unit>({ onCommit(it); haptics.endSeek(committed = true) })
    val cancel by rememberUpdatedState<() -> Unit>({ onCancel(); haptics.endSeek(committed = false) })
    val ink = if (pixel) ExpressivePlayerStyle.PixelInk else ExpressivePlayerStyle.PosterInk
    Canvas(modifier.height(48.dp)
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
            if (!enabled) disabled()
            setProgress { if (enabled) commit(it.coerceIn(0f, 1f)); enabled }
        }
        .pointerInput(key, enabled, pixel) { if (enabled) detectTapGestures { commit(((it.x - (if (pixel) 0f else 12.dp.toPx())) / (size.width - (if (pixel) 0f else 24.dp.toPx())).coerceAtLeast(1f)).coerceIn(0f, 1f)) } }
        .pointerInput(key, enabled, pixel) {
            if (enabled) {
                var target = 0f
                detectHorizontalDragGestures(
                    onDragStart = { haptics.grab(); target = ((it.x - (if (pixel) 0f else 12.dp.toPx())) / (size.width - (if (pixel) 0f else 24.dp.toPx())).coerceAtLeast(1f)).coerceIn(0f, 1f); preview(target) },
                    onHorizontalDrag = { change, _ -> change.consume(); target = ((change.position.x - (if (pixel) 0f else 12.dp.toPx())) / (size.width - (if (pixel) 0f else 24.dp.toPx())).coerceAtLeast(1f)).coerceIn(0f, 1f); preview(target) },
                    onDragEnd = { commit(target) }, onDragCancel = { cancel() },
                )
            }
        }) {
        if (pixel) {
            val count = 14
            val step = size.width / count
            drawRect(ink, Offset(0f, center.y - 8.dp.toPx()), Size(size.width, 16.dp.toPx()), style = Stroke(1.dp.toPx()))
            for (i in 0 until count) {
                drawRect(ink.copy(alpha = if (enabled && (i + 0.5f) / count <= fraction) 1f else 0.2f),
                    Offset(i * step + 1.dp.toPx(), center.y - 6.dp.toPx()), Size((step - 2.dp.toPx()).coerceAtLeast(1f), 12.dp.toPx()))
            }
            val cursor = (size.width * fraction.coerceIn(0f, 1f)).coerceIn(4.dp.toPx(), (size.width - 4.dp.toPx()).coerceAtLeast(4.dp.toPx()))
            drawRect(ink.copy(alpha = if (enabled) 1f else 0.38f), Offset(cursor - 3.dp.toPx(), center.y - 11.dp.toPx()), Size(6.dp.toPx(), 22.dp.toPx()))
            drawRect(ExpressivePlayerStyle.PixelPaper, Offset(cursor - 1.dp.toPx(), center.y - 7.dp.toPx()), Size(2.dp.toPx(), 14.dp.toPx()))
        } else {
            val inset = 12.dp.toPx().coerceAtMost(size.width / 2f)
            val left = Offset(inset, center.y)
            val right = Offset(size.width - inset, center.y)
            val thumb = Offset(inset + (size.width - inset * 2f) * fraction.coerceIn(0f, 1f), center.y)
            val accent = ink.copy(alpha = if (enabled) 1f else 0.38f)
            drawEchoControlRail(left, right, left, thumb, accent, ink.copy(alpha = 0.18f))
            drawEchoControlThumb(thumb, accent, ExpressivePlayerStyle.PosterPaper, engaged, enabled)
        }
    }
}
