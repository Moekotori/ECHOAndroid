package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.EchoIcon
import app.echo.android.design.displayAlbum
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.design.formatDuration
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import kotlin.math.abs
import kotlin.math.sin

/**
 * The song as a conversation: a song card, the lyrics as incoming bubbles, a voice message that is the seek bar,
 * and an input bar for transport. There is no separate lyrics page; [lyricsContent] fills the thread.
 */
@Composable
internal fun ChatCoverPage(
    status: EchoPlaybackStatus,
    position: State<Long>,
    duration: State<Long>,
    actions: ExpressivePlayerActions,
    isFavorite: Boolean,
    castActive: Boolean,
    artworkScale: Float,
    lyricsContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = status.track
    val formats = remember(status.diagnostics) { playbackFormatChips(status.diagnostics, ::formatSampleRate).joinToString(" · ") }
    PlayerCoverLayout(
        minimumPortraitHeight = (560f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp,
        modifier = modifier,
        artwork = {
            ChatHeader(track, formats) {
                ChatIconButton(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    stringResource(if (isFavorite) R.string.feature_player_unfavorite_3a27e4 else R.string.feature_player_favorite_b5d1f5),
                    isFavorite, actions.favorite, size = 40.dp,
                )
                ChatIconButton(
                    Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.feature_player_queue_37fa6a), false, actions.queue, size = 40.dp,
                )
                actions.cast?.let {
                    ChatIconButton(
                        PlayerControlIcons.Cast,
                        stringResource(if (castActive) R.string.feature_player_cast_active else R.string.feature_player_cast),
                        castActive, it, size = 40.dp,
                    )
                }
            }
            SongCard(status, artworkScale, actions)
            lyricsContent(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp))
        },
        details = {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VoiceMessage(track?.id, position, duration, status.isPlaying, actions)
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp).shadow(4.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp))
                        .background(Color.White).height(60.dp).padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatIconButton(Icons.Rounded.Shuffle, stringResource(R.string.feature_player_shuffle), status.shuffleEnabled, actions.shuffle)
                    ChatIconButton(PlayerControlIcons.Previous, stringResource(R.string.feature_player_previous_af0264), false, actions.previous)
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(ChatStyle.Accent)
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.feature_player_play_or_pause_37a70f), onClick = actions.playPause),
                        contentAlignment = Alignment.Center,
                    ) {
                        EchoIcon(
                            if (status.isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
                            stringResource(R.string.feature_player_play_or_pause_37a70f), tint = Color.White, modifier = Modifier.size(24.dp),
                        )
                    }
                    ChatIconButton(PlayerControlIcons.Next, stringResource(R.string.feature_player_next_d67904), false, actions.next)
                    ChatIconButton(
                        if (status.repeatMode == EchoRepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        stringResource(
                            when (status.repeatMode) {
                                EchoRepeatMode.Off -> R.string.feature_player_repeat_off_254ca2
                                EchoRepeatMode.One -> R.string.feature_player_repeat_one_3df94f
                                EchoRepeatMode.All -> R.string.feature_player_repeat_all_751078
                            },
                        ),
                        status.repeatMode != EchoRepeatMode.Off, actions.repeat,
                    )
                }
            }
        },
    )
}

/** The track as one compact message: a small picture, the title and album, and a door to the lyric settings. */
@Composable
private fun SongCard(status: EchoPlaybackStatus, artworkScale: Float, actions: ExpressivePlayerActions) {
    val track = status.track
    val side = (60.dp * artworkScale).coerceIn(44.dp, 88.dp)
    Row(
        Modifier.fillMaxWidth().shadow(1.dp, ChatStyle.Bubble).clip(ChatStyle.Bubble).background(Color.White).padding(7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        EchoArtworkImage(track?.artworkUri, track?.title, Modifier.size(side), RoundedCornerShape(12.dp), EchoArtworkSize.Card)
        Column(Modifier.weight(1f)) {
            Text(
                track?.let { displayTrack(it.title) } ?: stringResource(R.string.feature_player_not_playing_d72324),
                color = ChatStyle.Ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(track?.let { displayAlbum(it.album) }?.takeIf { it.isNotBlank() }, track?.let { displayArtist(it.artist) }).joinToString(" · "),
                Modifier.openArtistWhen(track?.id, track?.artist, actions.openArtist),
                color = ChatStyle.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        ChatIconButton(PlayerControlIcons.Lyrics, stringResource(R.string.feature_player_lyrics_settings_843bc9), false, actions.lyrics, size = 40.dp)
    }
}

/** A neutral icon; an active toggle gets a soft accent disc behind it instead of a tinted glyph. */
@Composable
private fun ChatIconButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 48.dp,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (active) ChatStyle.Accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { selected = active },
        contentAlignment = Alignment.Center,
    ) {
        EchoIcon(icon, label, tint = ChatStyle.Ink, modifier = Modifier.size(if (size >= 48.dp) 24.dp else 22.dp))
    }
}

/** A voice message: play/pause on the left, a waveform that fills as the song plays and seeks on touch. */
@Composable
private fun VoiceMessage(trackKey: String?, position: State<Long>, duration: State<Long>, playing: Boolean, actions: ExpressivePlayerActions) {
    val bars = 30
    val seed = trackKey?.hashCode() ?: 0
    val levels = remember(seed) { FloatArray(bars) { i -> ((abs(sin(i * 1.7 + seed)) + abs(sin(i * 0.53 + seed))) / 2.0).toFloat() } }
    val label = stringResource(R.string.feature_player_play_or_pause_37a70f)
    Row(
        Modifier.widthIn(max = 360.dp).fillMaxWidth().shadow(3.dp, ChatStyle.Bubble).clip(ChatStyle.Bubble).background(ChatStyle.Accent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color.White).clickable(role = Role.Button, onClickLabel = label, onClick = actions.playPause),
            contentAlignment = Alignment.Center,
        ) {
            EchoIcon(if (playing) PlayerControlIcons.Pause else PlayerControlIcons.Play, label, tint = ChatStyle.Accent, modifier = Modifier.size(18.dp))
        }
        ThemedSeekBar(
            trackKey = trackKey, position = position, duration = duration, onSeek = actions.seek,
            modifier = Modifier.weight(1f), height = 32.dp, inset = 0.dp,
            times = { _, _, _ -> },
        ) { fraction, _, enabled ->
            val gap = 2.dp.toPx()
            val barWidth = (size.width - gap * (bars - 1)) / bars
            val filled = (fraction * bars).toInt()
            for (i in 0 until bars) {
                val h = (6.dp.toPx() + levels[i] * 20.dp.toPx()).coerceAtMost(size.height)
                val color = if (enabled && i < filled) Color.White else Color.White.copy(alpha = 0.4f)
                drawRoundRect(color, Offset(i * (barWidth + gap), (size.height - h) / 2f), Size(barWidth, h), CornerRadius(barWidth / 2f))
            }
        }
        ElapsedTotal(position, duration)
    }
}

@Composable
private fun ElapsedTotal(position: State<Long>, duration: State<Long>) {
    Text("${formatDuration(position.value)} / ${formatDuration(duration.value)}", color = Color.White,
        fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, maxLines = 1)
}
