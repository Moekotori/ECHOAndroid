package app.echo.android.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** Artist summaries are derived from loaded song pages; never imply they cover the full library. */
@Composable
internal fun LinkedArtistPagingControls(
    loadedTracks: Int,
    totalTracks: Int,
    loading: Boolean,
    onLoadMore: () -> Unit,
    error: String? = null,
) {
    if (loadedTracks >= totalTracks && error.isNullOrBlank()) return
    Column(Modifier.fillMaxWidth()) {
        if (loadedTracks < totalTracks) {
            Text(stringResource(R.string.linked_artists_partial, loadedTracks, totalTracks),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = onLoadMore, enabled = !loading) {
            Text(stringResource(if (error.isNullOrBlank()) R.string.linked_artists_load_more else R.string.library_retry))
        }
    }
}
