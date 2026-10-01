package app.echo.android.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.model.library.AlbumSummary

@Composable
internal fun RecentAlbumCard(album: AlbumSummary, mode: RecentActivityMode, onClick: () -> Unit) {
    // Recency is represented by the selected tab; keep the cover caption focused on the music.
    RecommendedAlbumCard(album, onClick)
}

@Composable
internal fun RecommendedAlbumCard(album: AlbumSummary, onClick: () -> Unit) {
    HomeArtworkCard(
        artworkUri = album.artworkUri,
        title = album.title,
        subtitle = album.albumArtist ?: album.artist ?: stringResource(R.string.feature_home_unknown_artist_85ee30),
        onClick = onClick,
    )
}

@Composable
internal fun HomeArtworkCard(artworkUri: String?, title: String, subtitle: String, onClick: () -> Unit) {
    // Two covers plus a glimpse of the next one communicate horizontal scrolling on a phone.
    val width = ((LocalConfiguration.current.screenWidthDp.dp - 64.dp) / 2.2f).coerceIn(120.dp, 160.dp)
    Column(
        modifier = Modifier.width(width).clip(RoundedCornerShape(6.dp))
            .homeCardClickable(onClick),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArtworkTile(artworkUri, modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            accent = MaterialTheme.colorScheme.primary, cornerRadius = 6.dp, elevation = 0.dp)
        Column(Modifier.heightIn(min = 52.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
