package app.echo.android.feature.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.formatDuration
import app.echo.android.model.connect.EchoRemoteTrack

@Composable
internal fun RemoteMusicRow(track: EchoRemoteTrack, current: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = !track.id.isNullOrBlank(),
        color = if (current) scheme.surfaceContainerLow else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().semantics { selected = current },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EchoArtworkImage(track.artworkUrl, null, Modifier.size(48.dp), shape = RoundedCornerShape(6.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium)
                Text(listOfNotNull(track.artist.takeIf(String::isNotBlank), track.album?.takeIf(String::isNotBlank)).joinToString(" · "),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            if (track.durationMs > 0L) Text(formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = scheme.onSurfaceVariant)
            Icon(if (current) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                stringResource(if (current) R.string.remote_current_track else R.string.feature_connect_play_on_pc_aa41d1),
                Modifier.size(20.dp), tint = when {
                    track.id.isNullOrBlank() -> scheme.onSurface.copy(alpha = 0.38f)
                    current -> scheme.primary
                    else -> scheme.onSurfaceVariant
                })
        }
    }
}
