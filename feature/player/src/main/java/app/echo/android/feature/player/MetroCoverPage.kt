package app.echo.android.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import app.echo.android.design.displayAlbum
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode

private const val StationCount = 5

/** The song as a metro line: arrival sign, platform board, a diagram where the train is the playhead, door-button controls. */
@Composable
internal fun MetroCoverPage(
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
    val formats = remember(status.diagnostics) { playbackFormatChips(status.diagnostics, ::formatSampleRate) }
    val sign = buildString {
        append("NOW  ")
        append(track?.let { displayTrack(it.title) } ?: stringResource(R.string.feature_player_not_playing_d72324))
        track?.let { append("  —  ").append(displayArtist(it.artist)) }
        if (formats.isNotEmpty()) append("  ·  ").append(formats.joinToString(" "))
    }
    val lyricsLabel = stringResource(R.string.feature_player_lyrics_b90c97)
    PlayerCoverLayout(
        minimumPortraitHeight = (700f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp,
        modifier = modifier,
        artwork = {
            Column(Modifier.fillMaxWidth().weight(1f)) {
                MetroLedSign(sign, animate)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White)
                        .border(2.dp, MetroStyle.Ink, RoundedCornerShape(10.dp)),
                ) {
                    Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
                        Box(Modifier.fillMaxWidth().height(10.dp).background(MetroStyle.Line))
                        Text(
                            track?.let { displayTrack(it.title) } ?: stringResource(R.string.feature_player_not_playing_d72324),
                            Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp), color = MetroStyle.Ink,
                            fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track?.let { displayArtist(it.artist) }.orEmpty(),
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp).openArtistWhen(track?.id, track?.artist, actions.openArtist),
                            color = MetroStyle.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track?.let { displayAlbum(it.album) }.orEmpty(),
                            Modifier.padding(horizontal = 14.dp), color = MetroStyle.Muted, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    EchoArtworkImage(
                        track?.artworkUri, track?.title,
                        Modifier.padding(12.dp).size(88.dp * artworkScale).clickable(onClickLabel = lyricsLabel, onClick = actions.lyrics),
                        RoundedCornerShape(6.dp), EchoArtworkSize.Card,
                    )
                }
                Spacer(Modifier.height(6.dp))
                MetroLineDiagram(position, duration, actions.seek, Modifier.fillMaxWidth().weight(1f))
            }
        },
        details = {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.player_style_metro_terminus) + " " + formatDuration(duration.value),
                    color = MetroStyle.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                ElapsedLabel(position)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                DoorButton(PlayerControlIcons.Previous, stringResource(R.string.feature_player_previous_af0264), 56.dp, false, actions.previous)
                DoorButton(
                    if (status.isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
                    stringResource(R.string.feature_player_play_or_pause_37a70f), 76.dp, true, actions.playPause,
                )
                DoorButton(PlayerControlIcons.Next, stringResource(R.string.feature_player_next_d67904), 56.dp, false, actions.next)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.feature_player_shuffle), status.shuffleEnabled, actions.shuffle)
                Pill(
                    stringResource(
                        when (status.repeatMode) {
                            EchoRepeatMode.Off -> R.string.feature_player_repeat_off_254ca2
                            EchoRepeatMode.One -> R.string.feature_player_repeat_one_3df94f
                            EchoRepeatMode.All -> R.string.feature_player_repeat_all_751078
                        },
                    ),
                    status.repeatMode != EchoRepeatMode.Off, actions.repeat,
                )
                Pill(stringResource(R.string.feature_player_queue_37fa6a), false, actions.queue)
                Pill(
                    stringResource(if (isFavorite) R.string.feature_player_unfavorite_3a27e4 else R.string.feature_player_favorite_b5d1f5),
                    isFavorite, actions.favorite,
                )
                actions.cast?.let {
                    Pill(stringResource(if (castActive) R.string.feature_player_cast_active else R.string.feature_player_cast), castActive, it)
                }
            }
        },
    )
}

@Composable
private fun ElapsedLabel(position: State<Long>) {
    Text(stringResource(R.string.player_style_metro_elapsed, formatDuration(position.value)),
        color = MetroStyle.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
}

/**
 * A vertical line with [StationCount] evenly spaced stops. The red part and the train follow playback;
 * tapping a stop seeks to it. Position is read while drawing, and the label emphasis changes only when a stop is passed.
 */
@Composable
private fun MetroLineDiagram(position: State<Long>, duration: State<Long>, onSeek: (Long) -> Unit, modifier: Modifier) {
    val reached by remember(position, duration) {
        derivedStateOf { (progressFraction(position.value, duration.value) * (StationCount - 1)).toInt().coerceIn(0, StationCount - 1) }
    }
    val total = duration.value
    BoxWithConstraints(modifier) {
        val pad = 18.dp
        val span = (maxHeight - pad * 2).coerceAtLeast(24.dp * StationCount)
        Canvas(Modifier.fillMaxSize()) {
            val x = 28.dp.toPx()
            val top = pad.toPx()
            val length = span.toPx()
            val fraction = progressFraction(position.value, duration.value)
            drawLine(MetroStyle.Rail, Offset(x, top), Offset(x, top + length), 10.dp.toPx(), StrokeCap.Round)
            if (fraction > 0f) drawLine(MetroStyle.Line, Offset(x, top), Offset(x, top + length * fraction), 10.dp.toPx(), StrokeCap.Round)
            for (k in 0 until StationCount) {
                val y = top + length * k / (StationCount - 1)
                val passed = fraction + 1e-4f >= k / (StationCount - 1f)
                drawCircle(Color.White, 11.dp.toPx(), Offset(x, y))
                drawCircle(if (passed) MetroStyle.Line else Color(0xFFAAB0BB), 11.dp.toPx(), Offset(x, y), style = Stroke(5.dp.toPx()))
            }
            val trainY = top + length * fraction
            val half = 15.dp.toPx()
            drawRoundRect(MetroStyle.Line, Offset(x - half, trainY - half), Size(half * 2, half * 2), CornerRadius(9.dp.toPx()))
            val tri = Path().apply {
                moveTo(x - 5.dp.toPx(), trainY - 4.dp.toPx())
                lineTo(x + 5.dp.toPx(), trainY - 4.dp.toPx())
                lineTo(x, trainY + 5.dp.toPx())
                close()
            }
            drawPath(tri, Color.White)
        }
        for (k in 0 until StationCount) {
            val y = pad + span * k / (StationCount - 1)
            val time = if (total > 0) total * k / (StationCount - 1) else 0L
            val label = when (k) {
                0 -> stringResource(R.string.player_style_metro_origin)
                StationCount - 1 -> stringResource(R.string.player_style_metro_terminus)
                else -> formatDuration(time)
            }
            val passed = k <= reached
            Row(
                Modifier.offset(y = y - 20.dp).fillMaxWidth().height(40.dp).padding(start = 56.dp, end = 4.dp)
                    .clickable(enabled = total > 0, role = Role.Button, onClickLabel = formatDuration(time)) { onSeek(time) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label, Modifier.weight(1f), color = if (passed) MetroStyle.Ink else Color(0xFF8B91A0),
                    fontWeight = if (k == reached) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(formatDuration(time), color = Color(0xFF8B91A0), fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
            }
        }
    }
}

@Composable
private fun DoorButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Dp, primary: Boolean, onClick: () -> Unit) {
    val fill = if (primary) MetroStyle.Line else Color.White
    Box(
        Modifier.size(size).clip(CircleShape).background(fill)
            .border(2.dp, if (primary) MetroStyle.Line else MetroStyle.Ink, CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { EchoIcon(icon, label, tint = if (primary) Color.White else MetroStyle.Ink, modifier = Modifier.size(size * 0.48f)) }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Pill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (active) MetroStyle.Ink else Color.White)
            .border(2.dp, MetroStyle.Ink, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label; selected = active }
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) Color.White else MetroStyle.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
    }
}
