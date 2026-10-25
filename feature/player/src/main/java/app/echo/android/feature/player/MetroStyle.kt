package app.echo.android.feature.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.model.playback.EchoTrackRef

/** Line-4 red on platform grey, with a black dot-matrix sign. Shared by the song and lyrics pages. */
internal object MetroStyle {
    val Paper = Color(0xFFF3F4F6)
    val Ink = Color(0xFF1C1E24)
    val Line = Color(0xFFE4002B)
    val Rail = Color(0xFFCFD3DA)
    val Muted = Color(0xFF5B6070)
    val Sign = Color(0xFF0A0A0A)
    val Amber = Color(0xFFFFB000)
}

/** The arrival sign: a line badge and amber text that scrolls only when it does not fit and motion is allowed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MetroLedSign(text: String, animate: Boolean, modifier: Modifier = Modifier) {
    val moving = animate && !LocalEchoEffectivePerformanceMode.current.isLightweight
    Row(
        modifier.fillMaxWidth().height(48.dp).background(MetroStyle.Sign, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).background(MetroStyle.Line, CircleShape), contentAlignment = Alignment.Center) {
            Text("4", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        Text(
            text, Modifier.weight(1f).padding(start = 10.dp).clipToBounds()
                .then(if (moving) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, velocity = 36.dp) else Modifier),
            color = MetroStyle.Amber, fontFamily = FontFamily.Monospace, fontSize = 14.sp, maxLines = 1, softWrap = false,
        )
    }
}

/** Lyrics page header: the sign shows the current track the way a platform sign shows the next train. */
@Composable
internal fun MetroLyricsHeading(track: EchoTrackRef, animate: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        MetroLedSign("NOW  ${displayTrack(track.title)}  —  ${displayArtist(track.artist)}", animate)
    }
}
