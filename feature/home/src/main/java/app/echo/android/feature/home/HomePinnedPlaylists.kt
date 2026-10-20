package app.echo.android.feature.home

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.model.library.EchoPlaylist

@Composable
internal fun HomePinnedPlaylists(playlists: List<EchoPlaylist>, onOpen: (EchoPlaylist) -> Unit, onPlay: (EchoPlaylist) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HomeSectionHeader(stringResource(R.string.home_pinned_playlists), Modifier.padding(horizontal = 24.dp))
        LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(playlists, key = { it.id }) { playlist ->
                Column(Modifier.width(148.dp).clip(RoundedCornerShape(HomeCoverCorner)).homeCardClickable { onOpen(playlist) },
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        ArtworkTile(playlist.artworkUri,
                            Modifier.fillMaxWidth().aspectRatio(1f),
                            accent = scheme.primary, cornerRadius = HomeCoverCorner)
                        // Quick-play sits on the cover; the visible disc is 36dp, the touch target stays 48dp.
                        FilledIconButton(onClick = { onPlay(playlist) }, enabled = playlist.trackCount > 0,
                            modifier = Modifier.padding(4.dp).size(36.dp), shape = RoundedCornerShape(HomeCoverCorner),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = scheme.primary, contentColor = scheme.onPrimary)) {
                            EchoIcon(Icons.Rounded.PlayArrow, stringResource(R.string.home_play_pinned),
                                tint = scheme.onPrimary, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(playlist.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pluralStringResource(R.plurals.home_pinned_track_count, playlist.trackCount, playlist.trackCount),
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
