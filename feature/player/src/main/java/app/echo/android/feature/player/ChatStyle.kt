package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.model.playback.EchoTrackRef

/** Lavender thread, white incoming bubbles, a violet accent for "you are here". */
internal object ChatStyle {
    val Paper = Color(0xFFECE9F5)
    val Ink = Color(0xFF26233A)
    val Accent = Color(0xFF6C5CE7)
    val Muted = Color(0xFF6F6A8A)
    val Bubble = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
}

/** The conversation header: the artwork as an avatar, the artist as the contact, and a status line. */
@Composable
internal fun ChatHeader(
    track: EchoTrackRef?,
    status: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EchoArtworkImage(track?.artworkUri, null, Modifier.size(38.dp).clip(CircleShape), CircleShape, EchoArtworkSize.Tiny)
        Column(Modifier.weight(1f)) {
            Text(track?.let { displayArtist(it.artist) }.orEmpty(), color = ChatStyle.Ink, fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, color = ChatStyle.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** Lyrics page header: the same contact row, with the song title as the status line. */
@Composable
internal fun ChatLyricsHeading(track: EchoTrackRef) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp).background(Color.Transparent)) {
        ChatHeader(track, displayTrack(track.title))
    }
}
