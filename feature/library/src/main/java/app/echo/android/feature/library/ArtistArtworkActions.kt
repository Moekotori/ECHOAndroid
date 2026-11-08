package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalArtistArtworkOverrides
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoArtistArtwork
import kotlinx.coroutines.CancellationException

class ArtistArtworkActions(val choose: (String) -> Unit, val clear: (String) -> Unit,
    val matchFolder: (String) -> Unit, val discover: suspend (String) -> Unit)
internal val LocalArtistArtworkActions = staticCompositionLocalOf<ArtistArtworkActions?> { null }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArtistArtworkControls(artist: ArtistSummary) {
    val actions = LocalArtistArtworkActions.current ?: return
    if (artist.name.length > 512) return
    LaunchedEffect(artist.name, actions) {
        try { actions.discover(artist.name) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
    }
    val profile = LocalArtistArtworkOverrides.current[EchoArtistArtwork.key(artist.name)]
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(when {
            profile?.manualUri != null -> R.string.artist_artwork_manual
            profile?.localUri != null -> R.string.artist_artwork_local
            else -> R.string.artist_artwork_automatic
        }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow {
            TextButton(onClick = { actions.choose(artist.name) }) { Text(stringResource(R.string.artist_artwork_choose)) }
            TextButton(onClick = { actions.matchFolder(artist.name) }) { Text(stringResource(R.string.artist_artwork_match)) }
            TextButton(onClick = { actions.clear(artist.name) }, enabled = profile?.manualUri != null) { Text(stringResource(R.string.artist_artwork_reset)) }
        }
    }
}

@Composable
fun ArtistArtworkFeedback(error: Boolean, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.artist_artwork_title)) },
        text = { if (error) Text(stringResource(R.string.artist_artwork_error)) else Column {
            CircularProgressIndicator(); Text(stringResource(R.string.artist_artwork_processing))
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_library_cancel_4c5fa5)) } })
}
