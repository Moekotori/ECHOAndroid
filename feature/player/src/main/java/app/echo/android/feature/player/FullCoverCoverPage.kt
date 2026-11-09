package app.echo.android.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.sharp.Repeat
import androidx.compose.material.icons.sharp.RepeatOne
import androidx.compose.material.icons.sharp.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.design.formatDuration
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode

internal val FullCoverTextShadow = Shadow(Color.Black.copy(alpha = 0.64f), Offset(0f, 2f), 12f)

/**
 * Song page of the full-cover style. The artwork and its surrounding colors are painted by
 * [FullCoverBackdrop]; this page adds the song details and playback controls.
 * Tapping the picture opens the lyrics.
 */
@Composable
internal fun FullCoverCoverPage(
    status: EchoPlaybackStatus,
    position: State<Long>,
    duration: State<Long>,
    actions: ExpressivePlayerActions,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = status.track
    val haptics = rememberEchoHapticPerformer()
    val shadow = remember { TextStyle(shadow = FullCoverTextShadow) }
    val formats = remember(status.diagnostics) {
        playbackFormatChips(status.diagnostics, ::formatSampleRate).take(2).joinToString(" · ")
    }
    val lyricsLabel = stringResource(R.string.feature_player_lyrics_b90c97)
    val muted = Color.White.copy(alpha = 0.82f)
    val timeStyle = remember { TextStyle(fontFeatureSettings = "tnum") }
    val seekInset = 6.dp
    PlayerCoverLayout(
        minimumPortraitHeight = (560f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp,
        modifier = modifier,
        artwork = {
            Box(Modifier.fillMaxWidth().weight(1f).clickable(onClickLabel = lyricsLabel, onClick = actions.lyrics))
        },
        details = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        track?.let { displayTrack(it.title) } ?: stringResource(R.string.feature_player_not_playing_d72324),
                        Modifier.fillMaxWidth(), color = Color.White, fontSize = 24.sp, lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.35).sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, style = shadow,
                    )
                    Text(
                        track?.let { displayArtist(it.artist) } ?: stringResource(R.string.feature_player_pick_a_song_to_start_68b6af),
                        Modifier.fillMaxWidth().openArtistWhen(track?.id, track?.artist, actions.openArtist),
                        color = muted, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = shadow,
                    )
                }
                if (status.track.supportsMusicFavorite()) PlayerControlButton(
                    if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    stringResource(if (isFavorite) R.string.feature_player_unfavorite_3a27e4 else R.string.feature_player_favorite_b5d1f5),
                    onClick = { if (track != null) actions.favorite() },
                    touchSize = 48.dp, iconSize = 22.dp, tint = if (isFavorite) Color.White else muted,
                )
                PlayerControlButton(
                    PlayerControlIcons.Lyrics, lyricsLabel, actions.lyrics,
                    touchSize = 48.dp, iconSize = 22.dp, tint = muted,
                )
            }
            Spacer(Modifier.height(6.dp))
            ThemedSeekBar(
                trackKey = track?.id, position = position, duration = duration, onSeek = actions.seek,
                height = 26.dp, inset = seekInset,
                times = { elapsed, total, _ ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = seekInset), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDuration(elapsed), Modifier.weight(1f), color = muted, fontSize = 11.sp,
                            style = timeStyle, maxLines = 1)
                        Text(
                            formats, Modifier.weight(2f).padding(horizontal = 8.dp), color = muted, fontSize = 11.sp,
                            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(formatDuration(total), Modifier.weight(1f), color = muted, fontSize = 11.sp,
                            textAlign = TextAlign.End, style = timeStyle, maxLines = 1)
                    }
                },
            ) { fraction, engaged, enabled ->
                val y = center.y
                val inset = seekInset.toPx().coerceAtMost(center.x)
                val end = size.width - inset
                val thumb = inset + (end - inset) * fraction
                val stroke = 2.dp.toPx()
                drawLine(Color.White.copy(alpha = 0.22f), Offset(inset, y), Offset(end, y), stroke, StrokeCap.Round)
                if (enabled && fraction > 0f) {
                    drawLine(Color.White.copy(alpha = 0.92f), Offset(inset, y), Offset(thumb, y), stroke, StrokeCap.Round)
                }
                if (enabled) drawCircle(Color.White, (if (engaged) 5.5.dp else 3.dp).toPx(), Offset(thumb, y))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerControlButton(
                    PlayerControlIcons.Previous, stringResource(R.string.feature_player_previous_af0264),
                    onClick = { haptics.tick(); actions.previous() },
                    touchSize = 56.dp, iconSize = 30.dp, tint = Color.White.copy(alpha = 0.92f),
                )
                PlayerControlButton(
                    if (status.isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
                    stringResource(R.string.feature_player_play_or_pause_37a70f),
                    onClick = { haptics.confirm(); actions.playPause() },
                    touchSize = 68.dp, iconSize = 44.dp, tint = Color.White,
                )
                PlayerControlButton(
                    PlayerControlIcons.Next, stringResource(R.string.feature_player_next_d67904),
                    onClick = { haptics.tick(); actions.next() },
                    touchSize = 56.dp, iconSize = 30.dp, tint = Color.White.copy(alpha = 0.92f),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerControlButton(
                    Icons.Sharp.Shuffle, stringResource(R.string.feature_player_shuffle),
                    onClick = { haptics.tick(); actions.shuffle() },
                    touchSize = 48.dp, iconSize = 21.dp, tint = if (status.shuffleEnabled) Color.White else muted,
                )
                PlayerControlButton(
                    if (status.repeatMode == EchoRepeatMode.One) Icons.Sharp.RepeatOne else Icons.Sharp.Repeat,
                    stringResource(
                        when (status.repeatMode) {
                            EchoRepeatMode.Off -> R.string.feature_player_repeat_off_254ca2
                            EchoRepeatMode.One -> R.string.feature_player_repeat_one_3df94f
                            EchoRepeatMode.All -> R.string.feature_player_repeat_all_751078
                        },
                    ),
                    onClick = { haptics.tick(); actions.repeat() },
                    touchSize = 48.dp, iconSize = 21.dp, tint = if (status.repeatMode != EchoRepeatMode.Off) Color.White else muted,
                )
                PlayerControlButton(
                    PlayerControlIcons.Queue, stringResource(R.string.feature_player_queue_37fa6a), actions.queue,
                    touchSize = 48.dp, iconSize = 21.dp, tint = muted,
                )
                PlayerControlButton(
                    PlayerControlIcons.More,
                    stringResource(R.string.feature_player_expand_playback_settings_cf64a0),
                    onClick = { haptics.tick(); actions.more() },
                    touchSize = 48.dp, iconSize = 23.dp, tint = muted,
                )
            }
        },
    )
}
