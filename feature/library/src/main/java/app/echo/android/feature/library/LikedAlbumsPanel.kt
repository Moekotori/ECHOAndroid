package app.echo.android.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.EchoIcon
import app.echo.android.model.library.AlbumSummary

@Composable
internal fun LikedAlbumsPanel(
    albums: LazyPagingItems<AlbumSummary>,
    query: String,
    onBack: () -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.feature_library_back_49093c))
            }
            Text(stringResource(R.string.album_liked_title), style = MaterialTheme.typography.titleMedium)
        }
        AlbumWall(
            albums = albums,
            onOpenAlbum = onOpenAlbum,
            emptyTitle = stringResource(if (query.isBlank()) R.string.album_liked_empty else R.string.album_liked_no_results),
            emptyDetail = if (query.isBlank()) stringResource(R.string.album_liked_hint) else null,
            modifier = Modifier.weight(1f),
        )
    }
}
