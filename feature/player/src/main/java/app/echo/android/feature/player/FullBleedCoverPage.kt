package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoIcon
import app.echo.android.design.displayAlbum
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.design.formatDuration
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.settings.EchoPlayerPageStyle

internal object FullBleedStyle {
    val Screen = Color(0xFF08080B)
}

/**
 * The song page of the full-bleed styles (halftone, pixel mosaic, contours, dither, glyph field). The cover is the field painted
 * behind the page (see [NowPlayingBackdrop]); this page only adds the title, a thin progress line and
 * transport. Tapping the field opens the lyrics.
 */
@Composable
internal fun FullBleedCoverPage(
    status: EchoPlaybackStatus,
    position: State<Long>,
    duration: State<Long>,
    actions: ExpressivePlayerActions,
    isFavorite: Boolean,
    castActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = status.track
    val shadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 18f))
    val formats = remember(status.diagnostics) { playbackFormatChips(status.diagnostics, ::formatSampleRate).joinToString(" · ") }
    val lyricsLabel = stringResource(R.string.feature_player_lyrics_b90c97)
    PlayerCoverLayout(
        minimumPortraitHeight = (680f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp,
        modifier = modifier,
        artwork = {
            Box(Modifier.fillMaxWidth().weight(1f).clickable(onClickLabel = lyricsLabel, onClick = actions.lyrics)) {
                Text(
                    listOf(track?.let { displayAlbum(it.album) }.orEmpty(), formats).filter { it.isNotBlank() }.joinToString("  ·  ").uppercase(java.util.Locale.ROOT),
                    Modifier.align(Alignment.TopStart).padding(top = 6.dp), color = Color.White.copy(alpha = 0.85f),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.6.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = shadow,
                )
            }
        },
        details = {
            Text(
                track?.let { displayTrack(it.title) } ?: stringResource(R.string.feature_player_not_playing_d72324),
                Modifier.fillMaxWidth(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 48.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis, style = shadow,
            )
            Text(
                track?.let { displayArtist(it.artist) } ?: stringResource(R.string.feature_player_pick_a_song_to_start_68b6af),
                Modifier.fillMaxWidth().padding(top = 6.dp).openArtistWhen(track?.id, track?.artist, actions.openArtist),
                color = Color.White.copy(alpha = 0.88f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = shadow,
            )
            Spacer(Modifier.height(14.dp))
            ThemedSeekBar(
                trackKey = track?.id, position = position, duration = duration, onSeek = actions.seek, height = 32.dp, inset = 0.dp,
                times = { elapsed, total, _ ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(elapsed), color = Color.White.copy(alpha = 0.8f), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp)
                        Text(formatDuration(total), color = Color.White.copy(alpha = 0.8f), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp)
                    }
                },
            ) { fraction, engaged, enabled ->
                val y = center.y
                drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), 4.dp.toPx())
                if (enabled && fraction > 0f) drawLine(Color.White, Offset(0f, y), Offset(size.width * fraction, y), 4.dp.toPx())
                if (engaged) drawCircle(Color.White, 7.dp.toPx(), Offset(size.width * fraction, y))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GlyphKey(PlayerControlIcons.Previous, stringResource(R.string.feature_player_previous_af0264), actions.previous)
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(Color.White)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.feature_player_play_or_pause_37a70f), onClick = actions.playPause),
                    contentAlignment = Alignment.Center,
                ) {
                    EchoIcon(
                        if (status.isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
                        stringResource(R.string.feature_player_play_or_pause_37a70f), tint = FullBleedStyle.Screen, modifier = Modifier.size(30.dp),
                    )
                }
                GlyphKey(PlayerControlIcons.Next, stringResource(R.string.feature_player_next_d67904), actions.next)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Toggle(stringResource(R.string.feature_player_shuffle), status.shuffleEnabled, actions.shuffle)
                Toggle(
                    stringResource(
                        when (status.repeatMode) {
                            EchoRepeatMode.Off -> R.string.feature_player_repeat_off_254ca2
                            EchoRepeatMode.One -> R.string.feature_player_repeat_one_3df94f
                            EchoRepeatMode.All -> R.string.feature_player_repeat_all_751078
                        },
                    ),
                    status.repeatMode != EchoRepeatMode.Off, actions.repeat,
                )
                Toggle(stringResource(R.string.feature_player_queue_37fa6a), false, actions.queue)
                Toggle(
                    stringResource(if (isFavorite) R.string.feature_player_unfavorite_3a27e4 else R.string.feature_player_favorite_b5d1f5),
                    isFavorite, actions.favorite,
                )
                actions.cast?.let { Toggle(stringResource(if (castActive) R.string.feature_player_cast_active else R.string.feature_player_cast), castActive, it) }
            }
        },
    )
}

@Composable
private fun GlyphKey(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        EchoIcon(icon, label, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun Toggle(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label.uppercase(java.util.Locale.ROOT),
        Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick).semantics { contentDescription = label; selected = active }
            .padding(vertical = 8.dp),
        color = Color.White.copy(alpha = if (active) 1f else 0.6f), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp, letterSpacing = 1.4.sp, maxLines = 1,
        style = TextStyle(textDecoration = if (active) androidx.compose.ui.text.style.TextDecoration.Underline else null),
    )
}

/** Styles that paint the cover as a field behind both pages instead of showing it as a picture. */
internal fun String.isFullBleedStyle(): Boolean =
    this == EchoPlayerPageStyle.Halftone.id || this == EchoPlayerPageStyle.PixelMosaic.id || this == EchoPlayerPageStyle.Contours.id ||
        this == EchoPlayerPageStyle.Dither.id || this == EchoPlayerPageStyle.GlyphField.id
