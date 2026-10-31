package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.displayAlbum
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.model.playback.EchoTrackRef

/** A platform nameplate: strong title, restrained catalogue details, a framed album print. */
@Composable
internal fun MetroTrackBoard(track: EchoTrackRef?, artworkScale: Float, actions: ExpressivePlayerActions, lyricsLabel: String) {
    Row(
        Modifier.fillMaxWidth().background(MetroStyle.Board, RoundedCornerShape(5.dp))
            .border(1.dp, MetroStyle.Rule, RoundedCornerShape(5.dp))
            .drawBehind {
                drawRect(MetroStyle.Line, Offset(16.dp.toPx(), 0f), Size(32.dp.toPx(), 3.dp.toPx()))
                drawLine(MetroStyle.Rule, Offset(16.dp.toPx(), size.height - 8.dp.toPx()),
                    Offset(size.width - 16.dp.toPx(), size.height - 8.dp.toPx()), 0.5.dp.toPx())
            }.padding(start = 16.dp, end = 12.dp, top = 17.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(R.string.player_style_metro_now), color = MetroStyle.Line,
                fontSize = 9.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
            Text(track?.let { displayTrack(it.title) } ?: stringResource(R.string.feature_player_not_playing_d72324),
                color = MetroStyle.Ink, fontSize = 25.sp, lineHeight = 31.sp, letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track?.let { displayArtist(it.artist) }.orEmpty(),
                Modifier.fillMaxWidth().openArtistWhen(track?.id, track?.artist, actions.openArtist),
                color = MetroStyle.Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            track?.album?.takeIf { it.isNotBlank() }?.let {
                Text(displayAlbum(it), color = MetroStyle.Muted, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.padding(bottom = 2.dp)
            .background(MetroStyle.Rule.copy(alpha = 0.5f), RoundedCornerShape(2.dp)).padding(1.dp)
            .background(Color.White).padding(5.dp)) {
            EchoArtworkImage(track?.artworkUri, track?.title,
                Modifier.size((84.dp * artworkScale).coerceIn(56.dp, 112.dp))
                    .clickable(role = Role.Button, onClickLabel = lyricsLabel, onClick = actions.lyrics),
                RoundedCornerShape(1.dp), EchoArtworkSize.Card)
        }
    }
}
