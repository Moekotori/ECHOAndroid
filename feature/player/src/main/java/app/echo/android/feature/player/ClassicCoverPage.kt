package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.EchoPlayerArtwork
import app.echo.android.model.playback.EchoPlaybackStatus

/** The original artwork-tinted cover, marquee metadata and waveform transport. */
@Composable
internal fun ClassicCoverPage(
    status: EchoPlaybackStatus,
    palette: ArtworkPalette,
    presentationExpanded: Boolean,
    positionMsState: State<Long>,
    durationMsState: State<Long>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onCast: (() -> Unit)?,
    castActive: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenArtist: ((trackId: String, artistName: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    artworkScale: Float = 1f,
) {
    val track = status.track
    var previousRequestedFrom by remember { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier) {
        val pageHeight = maxOf(maxHeight, (580f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).height(pageHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                NowPlayingArtworkLight(
                    palette = palette, expanded = presentationExpanded, gestureStrength = { 1f },
                    enabled = !track?.artworkUri.isNullOrBlank(), trackKey = track?.id,
                    modifier = Modifier.size(minOf(maxWidth, maxHeight) * artworkScale),
                ) {
                    NowPlayingTrackTransition(track, previousRequestedFrom, artwork = true, modifier = Modifier.fillMaxSize()) { displayedTrack ->
                        EchoPlayerArtwork(
                            artworkUri = displayedTrack?.artworkUri, trackId = displayedTrack?.id,
                            expandedArtwork = true, contentDescription = displayedTrack?.title,
                            restingCornerRadius = 24.dp,
                            modifier = Modifier.fillMaxSize().clickable(onClick = onOpenLyrics),
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                NowPlayingTrackTransition(track, previousRequestedFrom, Modifier.fillMaxWidth()) { displayedTrack ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            displayedTrack?.title ?: stringResource(R.string.feature_player_not_playing_d72324),
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            color = OnArt, style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    displayedTrack?.artist ?: stringResource(R.string.feature_player_pick_a_song_to_start_68b6af),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .openArtistWhen(displayedTrack?.id, displayedTrack?.artist, onOpenArtist),
                                    color = OnArtMuted, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                displayedTrack?.album?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, color = OnArtMuted, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            GlyphButton(
                                if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                stringResource(if (isFavorite) R.string.feature_player_unfavorite_3a27e4 else R.string.feature_player_favorite_b5d1f5),
                                touchSize = 48.dp, iconSize = 22.dp, tint = OnArt, background = OnArtChip,
                                onClick = { if (displayedTrack != null && displayedTrack.id == track?.id) onToggleFavorite() },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val formats = remember(status.diagnostics) { playbackFormatChips(status.diagnostics, ::formatSampleRate) }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    formats.forEach { label ->
                        Text(label, color = OnArtMuted, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(OnArtChip).padding(horizontal = 9.dp, vertical = 4.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                NowPlayingScrubber(track?.id, positionMsState, durationMsState, onSeek)
                Spacer(Modifier.height(6.dp))
                NowPlayingControlDock(
                    isPlaying = status.isPlaying,
                    leadingIcon = PlayerControlIcons.Lyrics,
                    leadingDescription = stringResource(R.string.feature_player_lyrics_b90c97),
                    onLeadingAction = onOpenLyrics,
                    onPlayPause = onPlayPause,
                    onNext = { previousRequestedFrom = null; onNext() },
                    onPrevious = { previousRequestedFrom = track?.id; onPrevious() },
                    onOpenQueue = onOpenQueue, onCast = onCast, castActive = castActive,
                )
            }
        }
    }
}
