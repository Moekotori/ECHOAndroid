package app.echo.android.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.EchoIcon
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.displayAlbum
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.design.formatDuration
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The song as a conversation: a picture message, a title card, a voice message that is the seek bar, and an input bar for transport. */
@Composable
internal fun ChatCoverPage(
    status: EchoPlaybackStatus,
    position: State<Long>,
    duration: State<Long>,
    actions: ExpressivePlayerActions,
    isFavorite: Boolean,
    castActive: Boolean,
    artworkScale: Float,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = status.track
    val formats = remember(status.diagnostics) { playbackFormatChips(status.diagnostics, ::formatSampleRate).joinToString(" · ") }
    val lyricsLabel = stringResource(R.string.feature_player_lyrics_b90c97)
    PlayerCoverLayout(
        minimumPortraitHeight = (700f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp,
        modifier = modifier,
        artwork = {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                // Header, title card, voice bubble and typing dots take roughly 250dp; the picture gets the rest.
                val side = ((maxHeight - 250.dp).coerceIn(96.dp, 210.dp)) * artworkScale
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChatHeader(track, formats)
                    Box(Modifier.shadow(1.dp, ChatStyle.Bubble).clip(ChatStyle.Bubble).background(Color.White).padding(6.dp)) {
                        EchoArtworkImage(
                            track?.artworkUri, track?.title,
                            Modifier.size(side).clickable(onClickLabel = lyricsLabel, onClick = actions.lyrics),
                            RoundedCornerShape(14.dp), EchoArtworkSize.Hero,
                        )
                    }
                    Column(
                        Modifier.shadow(1.dp, ChatStyle.Bubble).clip(ChatStyle.Bubble).background(Color.White).padding(horizontal = 13.dp, vertical = 9.dp),
                    ) {
                        Text(
                            track?.let { displayTrack(it.title) } ?: stringResource(R.string.feature_player_not_playing_d72324),
                            color = ChatStyle.Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(track?.let { displayAlbum(it.album) }?.takeIf { it.isNotBlank() }, track?.let { displayArtist(it.artist) }).joinToString(" · "),
                            Modifier.openArtistWhen(track?.id, track?.artist, actions.openArtist),
                            color = ChatStyle.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    VoiceMessage(track?.id, position, duration, status.isPlaying, actions)
                    TypingBubble(running = animate && status.isPlaying)
                }
            }
        },
        details = {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.feature_player_shuffle), status.shuffleEnabled, actions.shuffle)
                Chip(
                    stringResource(
                        when (status.repeatMode) {
                            EchoRepeatMode.Off -> R.string.feature_player_repeat_off_254ca2
                            EchoRepeatMode.One -> R.string.feature_player_repeat_one_3df94f
                            EchoRepeatMode.All -> R.string.feature_player_repeat_all_751078
                        },
                    ),
                    status.repeatMode != EchoRepeatMode.Off, actions.repeat,
                )
                Chip(stringResource(R.string.feature_player_queue_37fa6a), false, actions.queue)
                Chip(
                    stringResource(if (isFavorite) R.string.feature_player_unfavorite_3a27e4 else R.string.feature_player_favorite_b5d1f5),
                    isFavorite, actions.favorite,
                )
                actions.cast?.let {
                    Chip(stringResource(if (castActive) R.string.feature_player_cast_active else R.string.feature_player_cast), castActive, it)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp).shadow(4.dp, RoundedCornerShape(33.dp)).clip(RoundedCornerShape(33.dp))
                    .background(Color.White).height(66.dp),
                horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
            ) {
                InputButton(PlayerControlIcons.Previous, stringResource(R.string.feature_player_previous_af0264), actions.previous)
                Box(
                    Modifier.size(50.dp).clip(CircleShape).background(ChatStyle.Accent)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.feature_player_play_or_pause_37a70f), onClick = actions.playPause),
                    contentAlignment = Alignment.Center,
                ) {
                    EchoIcon(
                        if (status.isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
                        stringResource(R.string.feature_player_play_or_pause_37a70f), tint = Color.White, modifier = Modifier.size(26.dp),
                    )
                }
                InputButton(PlayerControlIcons.Next, stringResource(R.string.feature_player_next_d67904), actions.next)
            }
        },
    )
}

@Composable
private fun InputButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        EchoIcon(icon, label, tint = ChatStyle.Ink, modifier = Modifier.size(26.dp))
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
        Modifier.width(300.dp).shadow(3.dp, ChatStyle.Bubble).clip(ChatStyle.Bubble).background(ChatStyle.Accent).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color.White).clickable(role = Role.Button, onClickLabel = label, onClick = actions.playPause),
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

/** Three dots that pulse while a song plays and the page is visible; a still bubble otherwise. */
@Composable
private fun TypingBubble(running: Boolean) {
    val moving = running && !LocalEchoEffectivePerformanceMode.current.isLightweight
    val clock = rememberLoopClock(moving, 2)
    Canvas(Modifier.shadow(1.dp, ChatStyle.Bubble).clip(ChatStyle.Bubble).background(Color.White).size(width = 56.dp, height = 30.dp)) {
        for (i in 0 until 3) {
            val alpha = if (moving) 0.35f + 0.65f * (0.5f + 0.5f * sin((clock.value - i * 0.12f) * 2f * PI.toFloat())) else 0.6f
            drawCircle(Color(0xFFA8A3C9).copy(alpha = alpha), 3.5.dp.toPx(), Offset(size.width / 2f + (i - 1) * 11.dp.toPx(), size.height / 2f))
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clip(RoundedCornerShape(16.dp)).background(if (active) ChatStyle.Accent else Color.White)
            .border(1.5.dp, ChatStyle.Accent, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label; selected = active }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        color = if (active) Color.White else ChatStyle.Accent, fontSize = 12.5.sp, maxLines = 1,
    )
}
