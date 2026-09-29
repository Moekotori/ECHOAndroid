package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.QueueMusic
import androidx.compose.material.icons.sharp.Repeat
import androidx.compose.material.icons.sharp.RepeatOne
import androidx.compose.material.icons.sharp.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode

internal data class ExpressivePlayerActions(
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seek: (Long) -> Unit,
    val queue: () -> Unit,
    val cast: (() -> Unit)?,
    val shuffle: () -> Unit,
    val repeat: () -> Unit,
    val favorite: () -> Unit,
    val lyrics: () -> Unit,
    val openArtist: ((trackId: String, artistName: String) -> Unit)? = null,
)

@Composable
internal fun ExpressiveTransport(pixel: Boolean, isPlaying: Boolean, actions: ExpressivePlayerActions) {
    val ink = if (pixel) ExpressivePlayerStyle.PixelInk else ExpressivePlayerStyle.PosterInk
    val paper = if (pixel) ExpressivePlayerStyle.PixelPaper else ExpressivePlayerStyle.PosterPaper
    val pauseIcon = if (pixel) {
        if (isPlaying) PixelPlayerIcons.Pause else PixelPlayerIcons.Play
    } else if (isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play
    val playLabel = stringResource(R.string.feature_player_play_or_pause_37a70f)
    val previousLabel = stringResource(R.string.feature_player_previous_af0264)
    val nextLabel = stringResource(R.string.feature_player_next_d67904)
    if (pixel) {
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TransportTile(pauseIcon, playLabel, actions.playPause, paper, ink,
                Modifier.weight(1f).fillMaxHeight(), large = true, pixel = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TransportTile(PixelPlayerIcons.Previous, previousLabel, actions.previous, paper, ink,
                    Modifier.weight(1f).fillMaxWidth(), pixel = true)
                TransportTile(PixelPlayerIcons.Next, nextLabel, actions.next, paper, ink,
                    Modifier.weight(1f).fillMaxWidth(), pixel = true)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().height(80.dp).background(ink), verticalAlignment = Alignment.CenterVertically) {
            TransportTile(PlayerControlIcons.Previous, previousLabel, actions.previous, Color.White, ink,
                Modifier.weight(1f).fillMaxHeight())
            TransportTile(pauseIcon, playLabel, actions.playPause, paper, ink,
                Modifier.weight(1.2f).fillMaxHeight(), large = true)
            TransportTile(PlayerControlIcons.Next, nextLabel, actions.next, Color.White, ink,
                Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun TransportTile(icon: ImageVector, label: String, click: () -> Unit, tint: Color, background: Color,
                          modifier: Modifier, large: Boolean = false, pixel: Boolean = false) {
    val surface = if (pixel) Modifier.drawWithCache {
        val outline = PixelFrameShape.createOutline(size, layoutDirection, this)
        onDrawBehind { translate(4.dp.toPx(), 4.dp.toPx()) { drawOutline(outline, Color(0xFF94B471)) } }
    }.clip(PixelFrameShape) else Modifier
    Box(modifier.then(surface).background(background).clickable(role = Role.Button, onClick = click), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(if (pixel) { if (large) 96.dp else 60.dp } else if (large) 62.dp else 42.dp))
    }
}

@Composable
internal fun ExpressiveUtilities(pixel: Boolean, status: EchoPlaybackStatus, castActive: Boolean, actions: ExpressivePlayerActions) {
    val ink = if (pixel) ExpressivePlayerStyle.PixelInk else ExpressivePlayerStyle.PosterInk
    Row(Modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        UtilityIcon(if (pixel) PixelPlayerIcons.Shuffle else Icons.Sharp.Shuffle, stringResource(R.string.feature_player_shuffle), ink, status.shuffleEnabled, actions.shuffle)
        UtilityIcon(if (pixel && status.repeatMode != EchoRepeatMode.One) PixelPlayerIcons.Repeat else if (status.repeatMode == EchoRepeatMode.One) Icons.Sharp.RepeatOne else Icons.Sharp.Repeat,
            stringResource(when (status.repeatMode) {
                EchoRepeatMode.Off -> R.string.feature_player_repeat_off_254ca2
                EchoRepeatMode.One -> R.string.feature_player_repeat_one_3df94f
                EchoRepeatMode.All -> R.string.feature_player_repeat_all_751078
            }), ink, status.repeatMode != EchoRepeatMode.Off, actions.repeat)
        UtilityIcon(if (pixel) PixelPlayerIcons.Queue else Icons.AutoMirrored.Sharp.QueueMusic, stringResource(R.string.feature_player_queue_37fa6a), ink, false, actions.queue)
        actions.cast?.let {
            UtilityIcon(if (pixel) PixelPlayerIcons.Cast else PlayerControlIcons.Cast,
                stringResource(if (castActive) R.string.feature_player_cast_active else R.string.feature_player_cast), ink, castActive, it)
        }
    }
}

@Composable
private fun UtilityIcon(icon: ImageVector, label: String, ink: Color, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick, modifier = Modifier.size(48.dp).semantics { selected = active }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, tint = ink, modifier = Modifier.size(30.dp))
            Box(Modifier.size(3.dp).background(if (active) ink else Color.Transparent))
        }
    }
}
