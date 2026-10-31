package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/** Song details as a diary page: taped photo, handwritten title, a dashed path for progress, sticker buttons. */
@Composable
internal fun ScrapbookCoverPage(
    status: EchoPlaybackStatus,
    position: State<Long>,
    duration: State<Long>,
    actions: ExpressivePlayerActions,
    isFavorite: Boolean,
    castActive: Boolean,
    artworkScale: Float,
    modifier: Modifier = Modifier,
) {
    val track = status.track
    val ink = ScrapbookStyle.Ink
    val formats = remember(status.diagnostics) { playbackFormatChips(status.diagnostics, ::formatSampleRate) }
    val lyricsLabel = stringResource(R.string.feature_player_lyrics_b90c97)
    PlayerCoverLayout(
        minimumPortraitHeight = (700f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp,
        modifier = modifier,
        artwork = {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                val width = (minOf(maxWidth * 0.74f, maxHeight / 1.2f) * artworkScale).coerceAtLeast(110.dp)
                Box(Modifier.width(width).height(width * 1.2f)) {
                    Column(
                        Modifier.fillMaxSize().rotate(-4f).shadow(8.dp, RectangleShape).background(Color.White)
                            .padding(start = 12.dp, top = 12.dp, end = 12.dp),
                    ) {
                        EchoArtworkImage(
                            track?.artworkUri, track?.title,
                            Modifier.fillMaxWidth().aspectRatio(1f)
                                .clickable(onClickLabel = lyricsLabel, onClick = actions.lyrics),
                            RectangleShape, EchoArtworkSize.Hero,
                        )
                        Text(
                            track?.let { displayAlbum(it.album) }.orEmpty() + " ♪",
                            Modifier.fillMaxWidth().padding(top = 8.dp), color = ScrapbookStyle.Muted,
                            fontFamily = ScrapbookStyle.Hand, fontSize = 18.sp, textAlign = TextAlign.Center,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    WashiTape(Modifier.align(Alignment.TopStart).offset((-14).dp, (-4).dp), degrees = -30f)
                    WashiTape(Modifier.align(Alignment.TopEnd).offset(14.dp, (-4).dp), degrees = 30f)
                    if (formats.isNotEmpty()) {
                        Column(
                            Modifier.align(Alignment.BottomEnd).offset(18.dp, 14.dp).size(76.dp)
                                .sticker(ScrapbookStyle.Yellow, 14f),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            formats.take(2).forEach {
                                Text(it, color = ScrapbookStyle.Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold, fontSize = 10.5.sp, maxLines = 1)
                            }
                        }
                    }
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(20.dp, 18.dp).size(48.dp).sticker(ScrapbookStyle.Mint, -12f)
                            .clickable(
                                enabled = track != null, role = Role.Button,
                                onClickLabel = stringResource(if (isFavorite) R.string.feature_player_unfavorite_3a27e4 else R.string.feature_player_favorite_b5d1f5),
                                onClick = actions.favorite,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        EchoIcon(if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, tint = ink, modifier = Modifier.size(26.dp))
                    }
                }
            }
        },
        details = {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.highlighter()) {
                    Text(
                        track?.let { displayTrack(it.title) } ?: stringResource(R.string.feature_player_not_playing_d72324),
                        color = ink, fontFamily = ScrapbookStyle.Hand,
                        fontSize = 34.sp, lineHeight = 40.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "— " + (track?.let { displayArtist(it.artist) } ?: stringResource(R.string.feature_player_pick_a_song_to_start_68b6af)),
                    Modifier.fillMaxWidth().openArtistWhen(track?.id, track?.artist, actions.openArtist),
                    color = ScrapbookStyle.Muted, fontFamily = ScrapbookStyle.Hand, fontSize = 19.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            val density = LocalDensity.current
            val dash = remember(density) { PathEffect.dashPathEffect(floatArrayOf(with(density) { 8.dp.toPx() }, with(density) { 7.dp.toPx() })) }
            ThemedSeekBar(
                trackKey = track?.id, position = position, duration = duration, onSeek = actions.seek, height = 44.dp,
                times = { elapsed, total, _ ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(elapsed), color = ScrapbookStyle.Muted, fontFamily = ScrapbookStyle.Hand, fontSize = 16.sp)
                        Text(formatDuration(total), color = ScrapbookStyle.Muted, fontFamily = ScrapbookStyle.Hand, fontSize = 16.sp)
                    }
                },
            ) { fraction, engaged, enabled ->
                val inset = 12.dp.toPx()
                val left = Offset(inset, center.y)
                val right = Offset(size.width - inset, center.y)
                val thumb = Offset(inset + (size.width - inset * 2f) * fraction, center.y)
                drawLine(ScrapbookStyle.Muted.copy(alpha = 0.55f), left, right, 3.dp.toPx(), pathEffect = dash)
                if (enabled && thumb.x > left.x) drawLine(ScrapbookStyle.Muted, left, thumb, 3.dp.toPx(), StrokeCap.Round)
                val radius = if (engaged) 18.dp.toPx() else 15.dp.toPx()
                drawCircle(Color.Black.copy(alpha = 0.16f), radius, thumb + Offset(0f, 2.dp.toPx()))
                drawCircle(Color.White, radius, thumb)
                drawCircle(ScrapbookStyle.Yellow, radius - 3.dp.toPx(), thumb)
                drawCircle(ScrapbookStyle.Paper, radius * 0.62f, thumb + Offset(radius * 0.34f, -radius * 0.28f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                StickerButton(PlayerControlIcons.Previous, stringResource(R.string.feature_player_previous_af0264), ScrapbookStyle.Mint, 58.dp, -6f, actions.previous)
                StickerButton(
                    if (status.isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
                    stringResource(R.string.feature_player_play_or_pause_37a70f), ScrapbookStyle.Pink, 84.dp, 4f, actions.playPause,
                )
                StickerButton(PlayerControlIcons.Next, stringResource(R.string.feature_player_next_d67904), ScrapbookStyle.Sky, 58.dp, 7f, actions.next)
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ChipButton(stringResource(R.string.feature_player_shuffle), status.shuffleEnabled, -2f, actions.shuffle)
                ChipButton(
                    stringResource(
                        when (status.repeatMode) {
                            EchoRepeatMode.Off -> R.string.feature_player_repeat_off_254ca2
                            EchoRepeatMode.One -> R.string.feature_player_repeat_one_3df94f
                            EchoRepeatMode.All -> R.string.feature_player_repeat_all_751078
                        },
                    ),
                    status.repeatMode != EchoRepeatMode.Off, 1.5f, actions.repeat,
                )
                ChipButton(stringResource(R.string.feature_player_queue_37fa6a), false, -1f, actions.queue)
                actions.cast?.let {
                    ChipButton(stringResource(if (castActive) R.string.feature_player_cast_active else R.string.feature_player_cast), castActive, 1f, it)
                }
            }
        },
    )
}

@Composable
private fun StickerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    size: androidx.compose.ui.unit.Dp,
    degrees: Float,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(size).sticker(color, degrees).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { EchoIcon(icon, label, tint = ScrapbookStyle.Ink, modifier = Modifier.size(size * 0.46f)) }
}

@Composable
private fun ChipButton(label: String, active: Boolean, degrees: Float, onClick: () -> Unit) {
    Box(Modifier.clickable(role = Role.Button, onClick = onClick).padding(6.dp)) {
        LabelChip(label.uppercase(java.util.Locale.ROOT), active, degrees)
    }
}
