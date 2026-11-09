package app.echo.android.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoDropdownMenuDivider
import app.echo.android.design.EchoDropdownMenuItem
import app.echo.android.design.LocalArtistArtworkOverrides
import app.echo.android.model.library.EchoArtistArtwork
import kotlinx.coroutines.CancellationException

class ArtistArtworkActions(val choose: (String) -> Unit, val clear: (String) -> Unit,
    val matchFolder: (String) -> Unit, val discover: suspend (String) -> Unit)
internal val LocalArtistArtworkActions = staticCompositionLocalOf<ArtistArtworkActions?> { null }

@Composable
internal fun DiscoverArtistArtwork(artistName: String, actions: ArtistArtworkActions) {
    LaunchedEffect(artistName, actions) {
        try { actions.discover(artistName) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
    }
}

@Composable
internal fun ArtistArtworkMenuItems(artistName: String, actions: ArtistArtworkActions, onDismiss: () -> Unit) {
    val profile = LocalArtistArtworkOverrides.current[EchoArtistArtwork.key(artistName)]
    val source = when {
        profile?.manualUri != null -> R.string.artist_artwork_manual
        profile?.localUri != null -> R.string.artist_artwork_local
        else -> R.string.artist_artwork_automatic
    }
    EchoDropdownMenuDivider()
    Text(
        stringResource(source),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    EchoDropdownMenuItem(
        text = { Text(stringResource(R.string.artist_artwork_choose)) },
        selected = false,
        onClick = {
            onDismiss()
            actions.choose(artistName)
        },
    )
    EchoDropdownMenuItem(
        text = { Text(stringResource(R.string.artist_artwork_match)) },
        selected = false,
        onClick = {
            onDismiss()
            actions.matchFolder(artistName)
        },
    )
    EchoDropdownMenuItem(
        text = { Text(stringResource(R.string.artist_artwork_reset)) },
        selected = false,
        enabled = profile?.manualUri != null,
        onClick = {
            onDismiss()
            actions.clear(artistName)
        },
    )
}

@Composable
fun ArtistArtworkFeedback(error: Boolean, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.artist_artwork_title)) },
        text = { if (error) Text(stringResource(R.string.artist_artwork_error)) else Column {
            CircularProgressIndicator(); Text(stringResource(R.string.artist_artwork_processing))
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_library_cancel_4c5fa5)) } })
}
