package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.model.playback.EchoTrackRef

/** Diary-page palette. Handwriting uses the platform cursive family, so no font ships with the app. */
internal object ScrapbookStyle {
    val Paper = Color(0xFFF3E8D2)
    val Ink = Color(0xFF3B2F4A)
    val Navy = Color(0xFF2E2A5A)
    val Muted = Color(0xFF6A5A80)
    val Pink = Color(0xFFF9B3C6)
    val Yellow = Color(0xFFFFE45C)
    val Mint = Color(0xFFA8E0D0)
    val Sky = Color(0xFFB9D4F5)
    val Hand = FontFamily.Cursive
}

/** A pastel highlighter stroke across the lower part of the text box. */
internal fun Modifier.highlighter(color: Color = ScrapbookStyle.Pink): Modifier = drawBehind {
    drawRoundRect(color.copy(alpha = 0.85f), Offset(-4.dp.toPx(), size.height * 0.55f),
        Size(size.width + 8.dp.toPx(), size.height * 0.36f), CornerRadius(3.dp.toPx()))
}

/** A die-cut sticker: colored disc, white border, soft shadow. */
internal fun Modifier.sticker(color: Color, degrees: Float = 0f): Modifier = this
    .rotate(degrees)
    .shadow(5.dp, CircleShape)
    .background(color, CircleShape)
    .border(4.dp, Color.White, CircleShape)

@Composable
internal fun WashiTape(modifier: Modifier = Modifier, width: Dp = 64.dp, height: Dp = 20.dp, degrees: Float = 0f) {
    Box(modifier.rotate(degrees).size(width, height).background(Color(0xFFFFE27A).copy(alpha = 0.78f), RectangleShape))
}

/** Notebook-page heading: handwritten title on a highlighter stroke beside a taped photo. */
@Composable
internal fun ScrapbookLyricsHeading(track: EchoTrackRef) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.highlighter()) {
                Text(displayTrack(track.title), color = ScrapbookStyle.Ink, fontFamily = ScrapbookStyle.Hand,
                    fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(displayArtist(track.artist), color = ScrapbookStyle.Muted, fontFamily = ScrapbookStyle.Hand,
                fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(64.dp)) {
            Box(Modifier.rotate(8f).shadow(4.dp, RectangleShape).background(Color.White).padding(4.dp)) {
                EchoArtworkImage(track.artworkUri, null, Modifier.size(56.dp), RectangleShape, EchoArtworkSize.Tiny)
            }
            WashiTape(Modifier.align(Alignment.TopCenter).padding(end = 6.dp), 34.dp, 12.dp, -8f)
        }
    }
}

@Composable
internal fun LabelChip(text: String, active: Boolean, degrees: Float, modifier: Modifier = Modifier) {
    Text(
        text, modifier.rotate(degrees)
            .background(if (active) ScrapbookStyle.Yellow else Color(0xFF1D1B22), RoundedCornerShape(3.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        color = if (active) ScrapbookStyle.Navy else Color.White,
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, letterSpacing = 1.4.sp,
        maxLines = 1,
    )
}
