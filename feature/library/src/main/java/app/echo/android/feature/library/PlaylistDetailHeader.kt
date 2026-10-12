package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoIcon
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoAccentColor
import app.echo.android.design.echoClickable
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.LibraryOfflinePin
import app.echo.android.feature.library.R as L10nR

internal data class PlaylistDetailColors(
    val surface: Color,
    val elevatedSurface: Color,
    val border: Color,
    val content: Color,
    val muted: Color,
)

@Composable
internal fun rememberPlaylistDetailColors(): PlaylistDetailColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        PlaylistDetailColors(
            surface = scheme.surface,
            elevatedSurface = scheme.surfaceContainer,
            border = scheme.outlineVariant.copy(alpha = 0.42f),
            content = scheme.onBackground,
            muted = scheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun PlaylistDetailTopBar(
    title: String,
    canEdit: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onArtwork: (() -> Unit)? = null,
    onEditRules: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = rememberPlaylistDetailColors()
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(L10nR.string.feature_library_back_49093c),
                tint = colors.content, modifier = Modifier.size(24.dp))
        }
        Text(title, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            color = colors.muted, style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (canEdit || onCopy != null || onExport != null || onEditRules != null) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    EchoIcon(Icons.Rounded.MoreVert,
                        contentDescription = stringResource(L10nR.string.feature_library_playlist_actions_044945),
                        tint = colors.content, modifier = Modifier.size(22.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false },
                    containerColor = MaterialTheme.colorScheme.surface) {
                    if (canEdit) DropdownMenuItem(
                        text = { Text(stringResource(L10nR.string.feature_library_rename_d1a28e)) },
                        leadingIcon = { EchoIcon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    onArtwork?.let { action -> DropdownMenuItem(text = { Text(stringResource(L10nR.string.playlist_change_cover)) },
                        onClick = { menuOpen = false; action() }) }
                    onEditRules?.let { action -> DropdownMenuItem(text = { Text(stringResource(L10nR.string.smart_edit_rules)) },
                        onClick = { menuOpen = false; action() }) }
                    onCopy?.let { action -> DropdownMenuItem(text = { Text(stringResource(L10nR.string.playlist_copy)) },
                        onClick = { menuOpen = false; action() }) }
                    onExport?.let { action -> DropdownMenuItem(text = { Text(stringResource(L10nR.string.feature_library_export_m3u_6f18a4)) },
                        onClick = { menuOpen = false; action() }) }
                    if (canEdit) DropdownMenuItem(
                        text = { Text(stringResource(L10nR.string.feature_library_delete_138ccf)) },
                        leadingIcon = { EchoIcon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
internal fun PlaylistHero(
    playlist: EchoPlaylist,
    coverUris: List<String>,
    durationMs: Long,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    offlinePin: LibraryOfflinePin? = null,
    onToggleOffline: (() -> Unit)? = null,
) {
    val fontScale = LocalDensity.current.fontScale
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 26.dp)) {
            // Keep long names and enlarged accessibility text out of a narrow side column.
            if (maxWidth < 320.dp || fontScale > 1.2f) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    PlaylistCover(playlist, coverUris, echoAccentColor(),
                        Modifier.size(156.dp).align(Alignment.CenterHorizontally))
                    PlaylistHeroText(playlist, durationMs)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    PlaylistCover(playlist, coverUris, echoAccentColor(), Modifier.size(124.dp))
                    PlaylistHeroText(playlist, durationMs, Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            PlaylistActionButton(Icons.Rounded.PlayArrow,
                stringResource(L10nR.string.feature_library_play_all_55c80e),
                onPlayAll, enabled = playlist.trackCount > 0)
            PlaylistActionButton(Icons.Rounded.Shuffle,
                stringResource(L10nR.string.feature_library_shuffle_34e7ce),
                onShuffle, enabled = playlist.trackCount > 0)
        }
        if (onToggleOffline != null) {
            Spacer(Modifier.height(10.dp))
            PlaylistActionButton(libraryOfflinePinIcon(offlinePin), libraryOfflinePinLabel(offlinePin),
                onToggleOffline)
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun PlaylistHeroText(playlist: EchoPlaylist, durationMs: Long, modifier: Modifier = Modifier) {
    val colors = rememberPlaylistDetailColors()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(playlistKindLabel(playlist), color = colors.muted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(playlistDisplayName(playlist), color = colors.content,
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(playlistHeroCaption(playlist, durationMs), color = colors.muted,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PlaylistActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = rememberPlaylistDetailColors()
    val content = colors.content.copy(alpha = if (enabled) 1f else 0.38f)
    Row(
        modifier = modifier.clip(RoundedCornerShape(6.dp))
            .echoClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            // Keep a comfortable touch target without a large visible button surface.
            .heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EchoIcon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = content, style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PlaylistCover(
    playlist: EchoPlaylist,
    coverUris: List<String>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .shadow(elevation = 5.dp, shape = shape, clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.16f))
            .clip(shape),
    ) {
        when {
            playlist.isLikedSongs && coverUris.isEmpty() -> PlaylistLikedCover(accent = accent)
            coverUris.size <= 1 -> ArtworkTile(
                artworkUri = coverUris.firstOrNull() ?: playlist.artworkUri,
                modifier = Modifier.fillMaxSize(),
                accent = accent,
                showSignal = coverUris.isEmpty() && playlist.artworkUri.isNullOrBlank(),
                cornerRadius = 0.dp,
                elevation = 0.dp,
                placeholderIconSize = 64.dp,
            )
            else -> PlaylistCoverMosaic(coverUris = coverUris, accent = accent)
        }
        // A fine sleeve edge stays local to the artwork, with no full-page effects.
        Box(Modifier.fillMaxSize().border(0.5.dp, Color.White.copy(alpha = 0.24f), shape))
    }
}

@Composable
private fun PlaylistLikedCover(accent: Color) {
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = if (dark) 0.88f else 0.92f),
                        accent.copy(alpha = 0.58f),
                        Color(0xFF3A2430),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        EchoIcon(
            Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.94f),
            modifier = Modifier.size(72.dp),
        )
    }
}

@Composable
private fun PlaylistCoverMosaic(
    coverUris: List<String>,
    accent: Color,
) {
    val cells = (coverUris + List(4) { null }).take(4)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f)) {
            MosaicCell(cells.getOrNull(0), accent, Modifier.weight(1f))
            MosaicCell(cells.getOrNull(1), accent, Modifier.weight(1f))
        }
        Row(Modifier.weight(1f)) {
            MosaicCell(cells.getOrNull(2), accent, Modifier.weight(1f))
            MosaicCell(cells.getOrNull(3), accent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MosaicCell(
    artworkUri: String?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(0.25.dp),
    ) {
        if (artworkUri.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                EchoIcon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            ArtworkTile(
                artworkUri = artworkUri,
                modifier = Modifier.fillMaxSize(),
                accent = accent,
                cornerRadius = 0.dp,
                elevation = 0.dp,
                placeholderIconSize = 22.dp,
            )
        }
    }
}
