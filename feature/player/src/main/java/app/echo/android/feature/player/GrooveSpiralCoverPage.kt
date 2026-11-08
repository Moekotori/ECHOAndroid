package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoIcon
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.displayAlbum
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.design.formatDuration
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode

/**
 * The song page of the groove spiral style: a record whose groove carries the track's facts, then the
 * title, a thin progress line and transport. Tapping the record opens the lyrics.
 */
@Composable
internal fun GrooveSpiralCoverPage(
    status: EchoPlaybackStatus,
    position: State<Long>,
    duration: State<Long>,
    actions: ExpressivePlayerActions,
    isFavorite: Boolean,
    artworkScale: Float,
    modifier: Modifier = Modifier,
) {
    val track = status.track
    val formats = remember(status.diagnostics) { playbackFormatChips(status.diagnostics, ::formatSampleRate) }
    val title = track?.let { displayTrack(it.title) } ?: stringResource(R.string.feature_player_not_playing_d72324)
    val artist = track?.let { displayArtist(it.artist) }.orEmpty()
    val album = track?.let { displayAlbum(it.album) }.orEmpty()
    val meta = remember(title, artist, album, formats) {
        GrooveText.Meta(listOf(title, artist, album, formats.joinToString(" ")).filter { it.isNotBlank() }.joinToString("  ·  "))
    }
    val spinning = status.isPlaying && !LocalEchoEffectivePerformanceMode.current.isLightweight
    val lyricsLabel = stringResource(R.string.feature_player_lyrics_b90c97)
    PlayerCoverLayout(
        minimumPortraitHeight = (700f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp,
        modifier = modifier,
        artwork = {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight) * artworkScale
                GrooveDisc(
                    track?.artworkUri, meta, spinning,
                    Modifier.size(side).clip(CircleShape).clickable(onClickLabel = lyricsLabel, onClick = actions.lyrics),
                )
            }
        },
        details = {
            Text(
                title, Modifier.fillMaxWidth().padding(top = 8.dp), color = GrooveSpiralStyle.Text,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist.ifBlank { stringResource(R.string.feature_player_pick_a_song_to_start_68b6af) },
                Modifier.fillMaxWidth().padding(top = 4.dp).openArtistWhen(track?.id, track?.artist, actions.openArtist),
                color = GrooveSpiralStyle.Dim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (formats.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) { formats.forEachIndexed { i, chip -> GrooveChip(chip, hot = i == 0) } }
            }
            Spacer(Modifier.height(12.dp))
            ThemedSeekBar(
                trackKey = track?.id, position = position, duration = duration, onSeek = actions.seek, height = 32.dp, inset = 0.dp,
                times = { elapsed, total, _ ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(elapsed), color = GrooveSpiralStyle.Dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Text(formatDuration(total), color = GrooveSpiralStyle.Dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                },
            ) { fraction, engaged, enabled ->
                val y = center.y
                drawLine(GrooveSpiralStyle.Dim.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), 3.dp.toPx())
                if (enabled && fraction > 0f) drawLine(GrooveSpiralStyle.Accent, Offset(0f, y), Offset(size.width * fraction, y), 3.dp.toPx())
                if (engaged) drawCircle(GrooveSpiralStyle.Accent, 7.dp.toPx(), Offset(size.width * fraction, y))
            }
            GrooveTransport(status.isPlaying, actions)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    GrooveToggle(stringResource(R.string.feature_player_shuffle), status.shuffleEnabled, actions.shuffle)
                    GrooveToggle(
                        stringResource(
                            when (status.repeatMode) {
                                EchoRepeatMode.Off -> R.string.feature_player_repeat_off_254ca2
                                EchoRepeatMode.One -> R.string.feature_player_repeat_one_3df94f
                                EchoRepeatMode.All -> R.string.feature_player_repeat_all_751078
                            },
                        ),
                        status.repeatMode != EchoRepeatMode.Off, actions.repeat,
                    )
                    GrooveToggle(stringResource(R.string.feature_player_queue_37fa6a), false, actions.queue)
                    if (status.track.supportsMusicFavorite()) GrooveToggle(
                        stringResource(if (isFavorite) R.string.feature_player_unfavorite_3a27e4 else R.string.feature_player_favorite_b5d1f5),
                        isFavorite, actions.favorite,
                    )
                }
                val moreLabel = stringResource(R.string.feature_player_expand_playback_settings_cf64a0)
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = moreLabel, onClick = actions.more),
                    contentAlignment = Alignment.Center,
                ) { EchoIcon(PlayerControlIcons.More, moreLabel, tint = GrooveSpiralStyle.Dim, modifier = Modifier.size(24.dp)) }
            }
        },
    )
}

@Composable
private fun GrooveTransport(isPlaying: Boolean, actions: ExpressivePlayerActions) {
    val play = stringResource(R.string.feature_player_play_or_pause_37a70f)
    val previous = stringResource(R.string.feature_player_previous_af0264)
    val next = stringResource(R.string.feature_player_next_d67904)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = previous, onClick = actions.previous), contentAlignment = Alignment.Center) {
            EchoIcon(PlayerControlIcons.Previous, previous, tint = GrooveSpiralStyle.Text, modifier = Modifier.size(28.dp))
        }
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(GrooveSpiralStyle.Accent)
                .clickable(role = Role.Button, onClickLabel = play, onClick = actions.playPause),
            contentAlignment = Alignment.Center,
        ) {
            EchoIcon(if (isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play, play, tint = GrooveSpiralStyle.Ink, modifier = Modifier.size(30.dp))
        }
        Box(Modifier.size(56.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = next, onClick = actions.next), contentAlignment = Alignment.Center) {
            EchoIcon(PlayerControlIcons.Next, next, tint = GrooveSpiralStyle.Text, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun GrooveChip(text: String, hot: Boolean) {
    val color = if (hot) GrooveSpiralStyle.Accent else GrooveSpiralStyle.Dim
    Text(
        text, Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1,
    )
}

@Composable
private fun GrooveToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label.uppercase(java.util.Locale.ROOT),
        Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label; selected = active }.padding(vertical = 8.dp),
        color = if (active) GrooveSpiralStyle.Accent else GrooveSpiralStyle.Dim, fontFamily = FontFamily.Monospace,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp, letterSpacing = 1.4.sp, maxLines = 1,
    )
}
