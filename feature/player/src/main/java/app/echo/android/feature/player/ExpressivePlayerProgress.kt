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
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
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
        ExpressiveSeekBar(pixel, fraction, total > 0, trackKey,
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
private fun ExpressiveSeekBar(pixel: Boolean, fraction: Float, enabled: Boolean, key: String?,
                              onPreview: (Float) -> Unit, onCommit: (Float) -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val cancel by rememberUpdatedState(onCancel)
    val ink = if (pixel) ExpressivePlayerStyle.PixelInk else ExpressivePlayerStyle.PosterInk
    Canvas(modifier.height(48.dp)
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
            if (!enabled) disabled()
            setProgress { if (enabled) commit(it.coerceIn(0f, 1f)); enabled }
        }
        .pointerInput(key, enabled) { if (enabled) detectTapGestures { commit((it.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)) } }
        .pointerInput(key, enabled) {
            if (enabled) {
                var target = 0f
                detectHorizontalDragGestures(
                    onDragStart = { target = (it.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f); preview(target) },
                    onHorizontalDrag = { change, _ -> change.consume(); target = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f); preview(target) },
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
        } else {
            drawLine(ink, Offset(0f, center.y), Offset(size.width, center.y), 1.dp.toPx())
            drawLine(ink, Offset(0f, center.y), Offset(size.width * fraction, center.y), 3.dp.toPx())
            drawRect(ink, Offset(size.width * fraction - 6.dp.toPx(), center.y - 8.dp.toPx()), Size(12.dp.toPx(), 16.dp.toPx()))
        }
    }
}
