package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Star
import androidx.compose.material.icons.sharp.StarBorder
import app.echo.android.design.EchoIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.echo.android.model.playback.EchoPlaybackStatus

@Composable
internal fun PixelHandheldCoverPage(status: EchoPlaybackStatus, position: State<Long>, duration: State<Long>,
                                   actions: ExpressivePlayerActions, isFavorite: Boolean, castActive: Boolean,
                                   artworkScale: Float, modifier: Modifier = Modifier) {
    val ink = ExpressivePlayerStyle.PixelInk
    val paper = ExpressivePlayerStyle.PixelPaper
    val track = status.track
    val fullTitle = track?.title ?: stringResource(R.string.feature_player_not_playing_d72324)
    val (title, edition) = remember(fullTitle) { recordSleeveTitleParts(fullTitle) }
    val formats = remember(status.diagnostics) { playbackFormatChips(status.diagnostics, ::formatSampleRate).joinToString(" · ") }
    PlayerCoverLayout(
        minimumPortraitHeight = (740f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp,
        modifier = modifier.background(paper),
        artwork = {
            Column(Modifier.fillMaxWidth().weight(1f).border(3.dp, ink, PixelFrameShape).padding(3.dp)) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    BasicText(title,
                        style = TextStyle(color = ink, fontFamily = ExpressivePlayerStyle.PixelDisplay,
                            fontWeight = FontWeight.Bold, lineHeight = 1.05.em),
                        autoSize = TextAutoSize.StepBased(22.sp, 36.sp, 1.sp), maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    Text(track?.artist ?: stringResource(R.string.feature_player_pick_a_song_to_start_68b6af),
                        modifier = Modifier.fillMaxWidth().openArtistWhen(track?.id, track?.artist, actions.openArtist),
                        color = ink, fontFamily = ExpressivePlayerStyle.PixelBody, fontSize = 27.sp,
                        lineHeight = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    edition?.let { Text(it, color = ink, fontFamily = ExpressivePlayerStyle.PixelBody,
                        fontSize = 16.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    PixelArtwork(track?.artworkUri, track?.title,
                        Modifier.width(maxWidth * artworkScale).height(maxHeight * artworkScale)
                            .clickable(onClickLabel = stringResource(R.string.feature_player_lyrics_b90c97), onClick = actions.lyrics))
                }
                ExpressiveProgress(true, track?.id, position, duration, actions.seek)
            }
        },
        details = {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track?.album.orEmpty(), color = ink, fontFamily = ExpressivePlayerStyle.PixelBody,
                        fontSize = 18.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(formats, color = ink, fontFamily = ExpressivePlayerStyle.PixelBody,
                        fontSize = 18.sp, lineHeight = 22.sp, maxLines = 1,
                        modifier = Modifier.horizontalScroll(rememberScrollState()))
                }
                IconButton(actions.favorite, enabled = track != null, modifier = Modifier.size(48.dp)
                    .background(if (isFavorite) ink.copy(alpha = 0.15f) else Color.Transparent, PixelFrameShape)
                    .semantics { selected = isFavorite }) {
                    EchoIcon(PixelPlayerIcons.Star,
                        stringResource(if (isFavorite) R.string.feature_player_unfavorite_3a27e4 else R.string.feature_player_favorite_b5d1f5),
                        tint = ink, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            ExpressiveTransport(true, status.isPlaying, actions)
            Spacer(Modifier.height(12.dp))
            ExpressiveUtilities(true, status, castActive, actions)
            Spacer(Modifier.height(6.dp))
        },
    )
}
