package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.library.LibraryOfflinePin

/** Album playback actions stay beside the tracks rather than occupying a separate row. */
@Composable
internal fun AlbumTracksToolbar(
    count: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayOnPc: (() -> Unit)? = null,
    offlinePin: LibraryOfflinePin? = null,
    onToggleOffline: (() -> Unit)? = null,
    onPinShortcut: ((Boolean) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.feature_library_tracks_2d80e8),
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    libraryTrackCountLabel(count),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AlbumPlaybackIconButton(
                icon = Icons.Rounded.PlayArrow,
                label = stringResource(R.string.feature_library_play_all_55c80e),
                filled = true,
                onClick = onPlayAll,
            )
            AlbumPlaybackIconButton(
                icon = Icons.Rounded.Shuffle,
                label = stringResource(R.string.feature_library_shuffle_34e7ce),
                filled = false,
                onClick = onShuffle,
            )
        }
        if (onPlayOnPc != null || onToggleOffline != null || onPinShortcut != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onPinShortcut != null) LibraryDesktopShortcutButton(onPinShortcut)
                if (onPlayOnPc != null) {
                    AlbumSecondaryAction(
                        icon = Icons.Rounded.PlayArrow,
                        label = stringResource(R.string.feature_library_play_on_pc_7c21a4),
                        onClick = onPlayOnPc,
                    )
                }
                if (onToggleOffline != null) {
                    AlbumSecondaryAction(
                        icon = libraryOfflinePinIcon(offlinePin),
                        label = libraryOfflinePinLabel(offlinePin),
                        onClick = onToggleOffline,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumPlaybackIconButton(
    icon: ImageVector,
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // The visual is 36 dp; the independent 48 dp targets remain easy to tap.
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Box(
            modifier = Modifier.size(36.dp).background(
                color = if (filled) scheme.surfaceVariant else scheme.surfaceVariant.copy(alpha = 0.4f),
                shape = CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            EchoIcon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AlbumSecondaryAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        EchoIcon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
