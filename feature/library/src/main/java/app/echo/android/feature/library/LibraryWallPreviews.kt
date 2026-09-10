package app.echo.android.feature.library

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoMobileTheme
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary

internal val LibraryWallPreviewAlbums = listOf(
    AlbumSummary(
        albumKey = "preview-evening-lines",
        title = "Evening Lines",
        albumArtist = "ECHO Preview",
        artist = "ECHO Preview",
        artworkUri = null,
        trackCount = 2,
        durationMs = 240_000L,
        year = 2026,
    ),
    AlbumSummary(
        albumKey = "preview-quiet-hours",
        title = "Quiet Hours",
        albumArtist = "Studio Preview",
        artist = "Studio Preview",
        artworkUri = null,
        trackCount = 1,
        durationMs = 180_000L,
        year = 2026,
    ),
    AlbumSummary(
        albumKey = "preview-soft-focus",
        title = "Soft Focus",
        albumArtist = "ECHO Preview",
        artist = "ECHO Preview",
        artworkUri = null,
        trackCount = 1,
        durationMs = 210_000L,
        year = 2026,
    ),
)

internal val LibraryWallPreviewArtists = listOf(
    ArtistSummary(
        artistKey = "preview-echo",
        name = "ECHO Preview",
        artworkUri = null,
        albumCount = 2,
        trackCount = 3,
        durationMs = 450_000L,
    ),
    ArtistSummary(
        artistKey = "preview-studio",
        name = "Studio Preview",
        artworkUri = null,
        albumCount = 1,
        trackCount = 1,
        durationMs = 180_000L,
    ),
    ArtistSummary(
        artistKey = "preview-field-signal",
        name = "Field Signal",
        artworkUri = null,
        albumCount = 1,
        trackCount = 2,
        durationMs = 300_000L,
    ),
)

@Preview(name = "Albums 3-up", showBackground = true, backgroundColor = 0xFF19191D, widthDp = 360, heightDp = 420)
@Composable
private fun AlbumWallThreeColumnPreview() {
    LibraryWallPreviewSurface {
        AlbumSummaryWall(
            albums = LibraryWallPreviewAlbums,
            onOpenAlbum = {},
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Preview(name = "Artists 3-up", showBackground = true, backgroundColor = 0xFF19191D, widthDp = 360, heightDp = 420)
@Composable
private fun ArtistWallThreeColumnPreview() {
    LibraryWallPreviewSurface {
        ArtistSummaryWall(
            artists = LibraryWallPreviewArtists,
            onOpenArtist = {},
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun LibraryWallPreviewSurface(content: @Composable () -> Unit) {
    EchoMobileTheme(darkTheme = true, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
