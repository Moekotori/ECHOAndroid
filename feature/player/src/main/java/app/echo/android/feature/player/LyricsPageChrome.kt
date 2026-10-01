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
) {
    if (track == null) return
    if (paper) {
        Column(
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(track.title, color = OnArt, fontFamily = FontFamily.Serif,
                fontSize = 23.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = OnArtMuted, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().openArtistWhen(track.id, track.artist, onOpenArtist),
                textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)))
    } else {
        LyricsTrackIdentity(
            track,
            Modifier.padding(top = 12.dp, bottom = 16.dp),
            artworkSize = 48,
            onOpenArtist = onOpenArtist,
        )
    }
}

@Composable
internal fun LyricsTrackIdentity(
    track: EchoTrackRef?,
    modifier: Modifier = Modifier,
    artworkSize: Int = 40,
    onOpenArtist: ((trackId: String, artistName: String) -> Unit)? = null,
) {
    if (track == null) return
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        EchoArtworkImage(track.artworkUri, null, Modifier.size(artworkSize.dp),
            shape = RoundedCornerShape(7.dp), sizeClass = EchoArtworkSize.Tiny)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(track.title, color = OnArt, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = OnArtMuted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().openArtistWhen(track.id, track.artist, onOpenArtist),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun LyricsTransportControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    onCast: (() -> Unit)?,
    castActive: Boolean,
) {
    val haptics = rememberEchoHapticPerformer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val separateCast = onCast != null && maxWidth < 296.dp
    Column {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        PlayerControlButton(PlayerControlIcons.Queue, stringResource(R.string.feature_player_queue_37fa6a),
            onOpenQueue, touchSize = 48.dp, iconSize = 22.dp, tint = OnArtMuted)
        PlayerControlButton(PlayerControlIcons.Previous, stringResource(R.string.feature_player_previous_af0264),
            { haptics.tick(); onPrevious() }, touchSize = 48.dp, iconSize = 30.dp, tint = OnArt)
        PlayerControlButton(if (isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
            stringResource(R.string.feature_player_play_or_pause_37a70f),
            touchSize = 64.dp, iconSize = 36.dp, tint = MaterialTheme.colorScheme.primary,
            onClick = { haptics.confirm(); onPlayPause() })
        PlayerControlButton(PlayerControlIcons.Next, stringResource(R.string.feature_player_next_d67904),
            { haptics.tick(); onNext() }, touchSize = 48.dp, iconSize = 30.dp, tint = OnArt)
        if (onCast != null && !separateCast) {
            PlayerControlButton(PlayerControlIcons.Cast, stringResource(R.string.feature_player_cast), onCast,
                touchSize = 48.dp, iconSize = 22.dp,
                tint = if (castActive) MaterialTheme.colorScheme.primary else OnArtMuted)
        }
        PlayerControlButton(PlayerControlIcons.Settings, stringResource(R.string.feature_player_lyrics_settings_843bc9),
            onOpenSettings, touchSize = 48.dp, iconSize = 22.dp, tint = OnArtMuted)
    }
    if (separateCast && onCast != null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PlayerControlButton(PlayerControlIcons.Cast, stringResource(R.string.feature_player_cast), onCast,
                touchSize = 48.dp, iconSize = 22.dp,
                tint = if (castActive) MaterialTheme.colorScheme.primary else OnArtMuted)
        }
    }
    }
    }
}
