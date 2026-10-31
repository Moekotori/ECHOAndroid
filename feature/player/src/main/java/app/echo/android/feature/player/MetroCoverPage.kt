package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Shuffle
import androidx.compose.material.icons.sharp.Repeat
import androidx.compose.material.icons.sharp.RepeatOne
import androidx.compose.material.icons.sharp.Favorite
import androidx.compose.material.icons.sharp.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode

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
        append(track?.let { displayTrack(it.title) } ?: stringResource(R.string.feature_player_not_playing_d72324))
        track?.let { append("  —  ").append(displayArtist(it.artist)) }
        if (formats.isNotEmpty()) append("  ·  ").append(formats.joinToString(" "))
    }
    val lyricsLabel = stringResource(R.string.feature_player_lyrics_b90c97)
    PlayerCoverLayout(
        minimumPortraitHeight = (720f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp,
        modifier = modifier,
        artwork = {
            MetroPlatformContent {
                MetroLedSign(sign, animate)
                Spacer(Modifier.height(12.dp))
                MetroTrackBoard(track, artworkScale, actions, lyricsLabel)
                Spacer(Modifier.height(6.dp))
                MetroLineDiagram(position, duration, status.isPlaying, actions.seek, Modifier.fillMaxWidth().weight(1f))
            }
        },
        details = {
            MetroJourneyReadout(position, duration)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                DoorButton(PlayerControlIcons.Previous, stringResource(R.string.feature_player_previous_af0264), 56.dp, false, actions.previous)
                DoorButton(
                    if (status.isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
                    stringResource(R.string.feature_player_play_or_pause_37a70f), 76.dp, true, actions.playPause,
                )
                DoorButton(PlayerControlIcons.Next, stringResource(R.string.feature_player_next_d67904), 56.dp, false, actions.next)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetroUtilityButton(Icons.Sharp.Shuffle, stringResource(R.string.feature_player_shuffle), status.shuffleEnabled, actions.shuffle)
                MetroUtilityButton(
                    if (status.repeatMode == EchoRepeatMode.One) Icons.Sharp.RepeatOne else Icons.Sharp.Repeat,
                    stringResource(
                        when (status.repeatMode) {
                            EchoRepeatMode.Off -> R.string.feature_player_repeat_off_254ca2
                            EchoRepeatMode.One -> R.string.feature_player_repeat_one_3df94f
                            EchoRepeatMode.All -> R.string.feature_player_repeat_all_751078
                        },
                    ),
                    status.repeatMode != EchoRepeatMode.Off, actions.repeat,
                )
                MetroUtilityButton(PlayerControlIcons.Queue, stringResource(R.string.feature_player_queue_37fa6a), false, actions.queue)
                MetroUtilityButton(
                    if (isFavorite) Icons.Sharp.Favorite else Icons.Sharp.FavoriteBorder,
                    stringResource(if (isFavorite) R.string.feature_player_unfavorite_3a27e4 else R.string.feature_player_favorite_b5d1f5),
                    isFavorite, actions.favorite,
                )
                actions.cast?.let {
                    MetroUtilityButton(PlayerControlIcons.Cast, stringResource(if (castActive) R.string.feature_player_cast_active else R.string.feature_player_cast), castActive, it)
                }
            }
        },
    )
}

/** Keep the platform reachable on short landscape windows without squeezing the station targets. */
@Composable
private fun ColumnScope.MetroPlatformContent(content: @Composable ColumnScope.() -> Unit) {
    if (LocalPlayerCoverLandscape.current) {
        val minimumHeight = (380f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .height(maxOf(maxHeight, minimumHeight)), content = content)
        }
    } else {
        Column(Modifier.fillMaxWidth().weight(1f), content = content)
    }
}

@Composable
private fun DoorButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Dp, primary: Boolean, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        Modifier.size(size).clip(shape)
            .background(if (primary) MetroStyle.SoftRed else Color.White.copy(alpha = 0.66f))
            .border(1.dp, if (primary) MetroStyle.Line.copy(alpha = 0.34f) else MetroStyle.Rule, shape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(if (primary) 5.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().background(if (primary) MetroStyle.Line else Color.Transparent, shape))
        EchoIcon(icon, label, tint = if (primary) Color.White else MetroStyle.Ink, modifier = Modifier.size(size * 0.43f))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MetroUtilityButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(4.dp))
            .background(if (active) Color.White else Color.Transparent)
            .then(if (active) Modifier.border(1.dp, MetroStyle.Rule, RoundedCornerShape(4.dp)) else Modifier)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { selected = active }
            .drawBehind {
                if (active) drawCircle(MetroStyle.Line, 2.dp.toPx(), Offset(size.width / 2f, size.height - 6.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) { EchoIcon(icon, label, tint = MetroStyle.Ink, modifier = Modifier.size(21.dp)) }
}
