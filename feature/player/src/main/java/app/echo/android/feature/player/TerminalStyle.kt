package app.echo.android.feature.player

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.model.playback.EchoTrackRef

/** Phosphor-green console palette. Cover and lyrics pages share it through [playerPageTokens]. */
internal object TerminalStyle {
    val Screen = Color(0xFF060A07)
    val Green = Color(0xFF7CFF9B)
    val Mono = FontFamily.Monospace
}

@Composable
internal fun TerminalWindowBar(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(30.dp).border(1.dp, TerminalStyle.Green),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(10.dp))
        repeat(3) { Box(Modifier.padding(end = 6.dp).size(9.dp).border(1.dp, TerminalStyle.Green, CircleShape)) }
        Text(title, Modifier.padding(start = 6.dp, end = 10.dp), color = TerminalStyle.Green.copy(alpha = 0.8f),
            fontFamily = TerminalStyle.Mono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun TerminalPrompt(command: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)) {
        Text("$ ", color = TerminalStyle.Green.copy(alpha = 0.5f), fontFamily = TerminalStyle.Mono, fontSize = 12.5.sp)
        Text(command, color = TerminalStyle.Green, fontFamily = TerminalStyle.Mono, fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold)
    }
}

/** Lyrics page header: a window bar and a comment line in place of the cover thumbnail. */
@Composable
internal fun TerminalLyricsHeading(track: EchoTrackRef) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)) {
        TerminalWindowBar("tail -f lyrics.lrc")
        Text("# ${displayTrack(track.title)} — ${displayArtist(track.artist)}",
            Modifier.padding(top = 10.dp), color = TerminalStyle.Green.copy(alpha = 0.5f),
            fontFamily = TerminalStyle.Mono, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Grayscale, green-multiplied and scanlined: cover art as seen on a phosphor screen. */
internal fun Modifier.phosphor(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        val gray = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), gray)
            drawContent()
            canvas.restore()
        }
        drawRect(TerminalStyle.Green, blendMode = BlendMode.Multiply)
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, y), Size(size.width, 1.dp.toPx()))
            y += step
        }
    }

/** `[mm:ss.xx]`, as in an LRC file. */
internal fun lyricTimestamp(ms: Long): String {
    val centis = (ms.coerceAtLeast(0L) / 10L) % 100L
    val seconds = (ms.coerceAtLeast(0L) / 1000L) % 60L
    val minutes = ms.coerceAtLeast(0L) / 60000L
    return "[%02d:%02d.%02d]".format(java.util.Locale.ROOT, minutes, seconds, centis)
}
