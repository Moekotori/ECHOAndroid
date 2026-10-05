package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.model.playback.EchoTrackRef

@Composable
internal fun LyricsTrackHeading(
    track: EchoTrackRef?,
    paper: Boolean,
    onOpenArtist: ((trackId: String, artistName: String) -> Unit)? = null,
    landscape: Boolean = false,
) {
    if (track == null) return
    if (paper) {
        Column(
            Modifier.fillMaxWidth().padding(top = if (landscape) 4.dp else 18.dp, bottom = if (landscape) 12.dp else 20.dp),
            horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(track.title, color = OnArt, fontFamily = LocalPlayerPageStyle.current.displayFont ?: FontFamily.Serif,
                fontSize = if (landscape) 21.sp else 23.sp, fontWeight = FontWeight.SemiBold,
                textAlign = if (landscape) TextAlign.Start else TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = OnArtMuted, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().openArtistWhen(track.id, track.artist, onOpenArtist),
                textAlign = if (landscape) TextAlign.Start else TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)))
    } else {
        LyricsTrackIdentity(
            track,
            Modifier.padding(top = if (landscape) 4.dp else 8.dp, bottom = 12.dp),
            artworkSize = if (landscape) 36 else 40,
            onOpenArtist = onOpenArtist,
            titleMaxLines = if (landscape) 2 else 1,
        )
    }
}

@Composable
internal fun LyricsTrackIdentity(
    track: EchoTrackRef?,
    modifier: Modifier = Modifier,
    artworkSize: Int = 40,
    onOpenArtist: ((trackId: String, artistName: String) -> Unit)? = null,
    titleMaxLines: Int = 1,
) {
    if (track == null) return
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EchoArtworkImage(track.artworkUri, null, Modifier.size(artworkSize.dp),
            shape = RoundedCornerShape(7.dp), sizeClass = EchoArtworkSize.Tiny)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(track.title, color = OnArt, style = MaterialTheme.typography.titleMedium,
                maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = OnArtMuted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().openArtistWhen(track.id, track.artist, onOpenArtist),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Symmetric side slots keep playback centered; secondary actions live in the header. */
@Composable
internal fun LyricsTransportControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val haptics = rememberEchoHapticPerformer()
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val controlsWidth = minOf(maxWidth, 360.dp)
        val compact = controlsWidth < 304.dp
        val transportGap = when {
            controlsWidth < 272.dp -> 0.dp
            compact -> 6.dp
            else -> 14.dp
        }
        val secondaryIconSize = if (compact) 18.dp else 20.dp
        val skipIconSize = if (compact) 24.dp else 26.dp
        val playIconSize = if (compact) 34.dp else 38.dp
        Row(
            Modifier.width(controlsWidth).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerControlButton(
                PlayerControlIcons.Queue, stringResource(R.string.feature_player_queue_37fa6a),
                onOpenQueue, touchSize = 48.dp, iconSize = secondaryIconSize, tint = OnArtMuted,
            )
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(transportGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerControlButton(
                    PlayerControlIcons.Previous, stringResource(R.string.feature_player_previous_af0264),
                    { haptics.tick(); onPrevious() }, touchSize = 48.dp, iconSize = skipIconSize, tint = OnArt,
                )
                PlayerControlButton(
                    if (isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
                    stringResource(R.string.feature_player_play_or_pause_37a70f),
                    touchSize = 60.dp, iconSize = playIconSize, tint = OnArt,
                    onClick = { haptics.confirm(); onPlayPause() },
                )
                PlayerControlButton(
                    PlayerControlIcons.Next, stringResource(R.string.feature_player_next_d67904),
                    { haptics.tick(); onNext() }, touchSize = 48.dp, iconSize = skipIconSize, tint = OnArt,
                )
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }
    }
}
