package app.echo.android.feature.player

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoPlayerArtwork
import app.echo.android.model.playback.EchoPlaybackStatus

/** Album, typography and transport on a single paper surface, as in the approved mock. */
@Composable
internal fun RecordSleeveCoverPage(
    status: EchoPlaybackStatus,
    positionMsState: State<Long>,
    durationMsState: State<Long>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onCast: (() -> Unit)?,
    castActive: Boolean,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    isCurrentTrackFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = status.track
    var previousRequestedFrom by remember { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier.background(RecordSleeveStyle.Paper)) {
        // Reserve the controls first. Small windows / enlarged text can scroll instead
        // of clipping controls or reducing their touch targets.
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val pageHeight = maxOf(maxHeight, (720f * fontScale).dp)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).height(pageHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = track?.album.orEmpty().uppercase(java.util.Locale.ROOT),
                    color = RecordSleeveStyle.Ink,
                    fontFamily = RecordSleeveStyle.BodyFont,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    letterSpacing = 2.5.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 36.dp),
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                NowPlayingTrackTransition(
                    track, previousRequestedFrom, artwork = true,
                    modifier = Modifier.size(minOf(maxWidth, maxHeight)),
                ) { displayedTrack ->
                    EchoPlayerArtwork(
                        artworkUri = displayedTrack?.artworkUri,
                        trackId = displayedTrack?.id,
                        expandedArtwork = true,
                        contentDescription = displayedTrack?.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClickLabel = stringResource(R.string.feature_player_lyrics_b90c97), onClick = onOpenLyrics),
                        restingCornerRadius = 0.dp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(RecordSleeveStyle.Rule))
            Spacer(Modifier.height(10.dp))
            NowPlayingTrackTransition(track, previousRequestedFrom, Modifier.fillMaxWidth()) { displayedTrack ->
                val fullTitle = displayedTrack?.title ?: stringResource(R.string.feature_player_not_playing_d72324)
                val (title, edition) = remember(fullTitle) { recordSleeveTitleParts(fullTitle) }
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        BasicText(
                            text = title,
                            style = TextStyle(
                                color = RecordSleeveStyle.Ink,
                                fontFamily = RecordSleeveStyle.TitleFont,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 1.08.em,
                                letterSpacing = (-0.5).sp,
                            ),
                            autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 46.sp, stepSize = 1.sp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        RecordSleeveIconButton(
                            if (isCurrentTrackFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            stringResource(if (isCurrentTrackFavorite) R.string.feature_player_unfavorite_3a27e4 else R.string.feature_player_favorite_b5d1f5),
                            onClick = onToggleFavorite,
                            enabled = displayedTrack != null && displayedTrack.id == track?.id,
                            iconSize = 29.dp,
                        )
                    }
                    if (edition != null) {
                        Text(
                            edition,
                            color = RecordSleeveStyle.Ink,
                            fontFamily = RecordSleeveStyle.BodyFont,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        displayedTrack?.artist ?: stringResource(R.string.feature_player_pick_a_song_to_start_68b6af),
                        color = RecordSleeveStyle.Ink,
                        fontFamily = RecordSleeveStyle.BodyFont,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val formatLabels = remember(status.diagnostics) {
                playbackFormatChips(status.diagnostics, ::formatSampleRate)
            }
            Text(
                formatLabels.joinToString("  /  "),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                color = RecordSleeveStyle.Ink,
                fontFamily = RecordSleeveStyle.BodyFont,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 1.4.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            RecordSleeveScrubber(track?.id, positionMsState, durationMsState, onSeek)
            Spacer(Modifier.height(8.dp))
            RecordSleeveTransport(
                isPlaying = status.isPlaying,
                shuffleEnabled = status.shuffleEnabled,
                repeatMode = status.repeatMode,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeatMode = onCycleRepeatMode,
                onPrevious = { previousRequestedFrom = track?.id; onPrevious() },
                onPlayPause = onPlayPause,
                onNext = { previousRequestedFrom = null; onNext() },
            )
            Spacer(Modifier.height(4.dp))
            RecordSleeveUtilities(onOpenQueue, onCast, castActive)
            Spacer(Modifier.height(8.dp))
        }
    }
}

private val TrailingEdition = Regex("^(.+?)\\s+(\\([^()]+\\))$")

/** Preserve edition metadata, but don't give it the main title's display size. */
internal fun recordSleeveTitleParts(title: String): Pair<String, String?> {
    val match = TrailingEdition.matchEntire(title) ?: return title to null
    return match.groupValues[1] to match.groupValues[2]
}
