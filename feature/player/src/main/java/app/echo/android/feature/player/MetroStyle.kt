package app.echo.android.feature.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.model.playback.EchoTrackRef

/** A single transit identity for the platform board, route map and lyric stations. */
internal object MetroStyle {
    val Paper = Color(0xFFF2F1EC)
    val Ink = Color(0xFF202A32)
    val Line = Color(0xFFD83139)
    val Rail = Color(0xFFCDD1CF)
    val Muted = Color(0xFF626D72)
    val Sign = Color(0xFF192124)
    val Amber = Color(0xFFFFC66B)
    val Rule = Color(0xFFDDDFD9)
    val SoftRed = Color(0xFFF8E7E3)
    val Board = Color(0xFFFCFCF8)
}

/** The arrival sign: a line badge and amber text that scrolls only when it does not fit and motion is allowed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MetroLedSign(text: String, animate: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    val moving = animate && !LocalEchoEffectivePerformanceMode.current.isLightweight
    Row(
        modifier.fillMaxWidth().heightIn(min = if (compact) 50.dp else 58.dp)
            .background(MetroStyle.Sign, RoundedCornerShape(5.dp))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(5.dp))
            .drawWithCache {
                val step = 5.dp.toPx()
                val radius = 0.6.dp.toPx()
                val dots = Path().apply {
                    var x = step
                    while (x < size.width) {
                        var y = step
                        while (y < size.height) {
                            addOval(Rect(x - radius, y - radius, x + radius, y + radius))
                            y += step
                        }
                        x += step
                    }
                }
                val screwInset = 4.dp.toPx()
                val screwRadius = 1.dp.toPx()
                onDrawBehind {
                    drawLine(Color.White.copy(alpha = 0.08f), Offset(8.dp.toPx(), 1.dp.toPx()),
                        Offset(size.width - 8.dp.toPx(), 1.dp.toPx()), 1.dp.toPx())
                    drawPath(dots, Color.White.copy(alpha = 0.035f))
                    val screw = Color.White.copy(alpha = 0.22f)
                    drawCircle(screw, screwRadius, Offset(screwInset, screwInset))
                    drawCircle(screw, screwRadius, Offset(size.width - screwInset, screwInset))
                    drawCircle(screw, screwRadius, Offset(screwInset, size.height - screwInset))
                    drawCircle(screw, screwRadius, Offset(size.width - screwInset, size.height - screwInset))
                }
            }.padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroLineBadge()
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.player_style_metro_line), color = Color.White.copy(alpha = 0.58f),
                fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Medium)
            Text(
                text, Modifier.fillMaxWidth().clipToBounds()
                    .then(if (moving) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, velocity = 30.dp) else Modifier),
                color = MetroStyle.Amber, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1, softWrap = false,
            )
        }
    }
}

@Composable
internal fun MetroLineBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(34.dp).background(MetroStyle.Line, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
        Text("04", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 19.sp)
    }
}

/** Lyrics page header: the sign shows the current track the way a platform sign shows the next train. */
@Composable
internal fun MetroLyricsHeading(track: EchoTrackRef, animate: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        MetroLedSign("${displayTrack(track.title)}  /  ${displayArtist(track.artist)}", animate)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.player_style_metro_lyric_route), color = MetroStyle.Ink,
                fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(stringResource(R.string.player_style_metro_seek_hint), color = MetroStyle.Muted, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}
