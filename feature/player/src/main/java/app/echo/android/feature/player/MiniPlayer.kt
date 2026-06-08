package app.echo.android.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.design.progressFraction
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus

@Composable
fun MiniPlayer(
    status: EchoPlaybackStatus,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 9.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.09f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White,
                        Color(0xFFFAFAFA),
                        Color(0xFFF4F4F5),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, Color(0xFFE9E9EC)), shape)
            .padding(start = 14.dp, top = 5.dp, end = 8.dp, bottom = 5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArtworkTile(
                artworkUri = status.track?.artworkUri,
                modifier = Modifier.size(36.dp),
                accent = EchoAccent,
                showSignal = false,
                cornerRadius = 8.dp,
                elevation = 1.dp,
                placeholderIconSize = 22.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    status.track?.title ?: "ECHO Mobile",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    color = RoonInk,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    status.track?.artist ?: "就绪",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = RoonMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                LinearProgressIndicator(
                    progress = { progressFraction(status.positionMs, status.durationMs) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(99.dp)),
                    color = Color(0xFFFF2D55).copy(alpha = 0.58f),
                    trackColor = Color(0xFFE8E8EA),
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .clickable(
                        enabled = status.state != EchoPlaybackState.Idle || status.track != null,
                        onClick = onPlayPause,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (status.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "播放或暂停",
                    tint = Color.Black.copy(alpha = 0.90f),
                    modifier = Modifier.size(29.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = {}),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "播放队列",
                    tint = Color.Black.copy(alpha = 0.64f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}
