package app.echo.android.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.model.library.ArtistSummary
import kotlinx.coroutines.CancellationException

/** Loaded only on demand; dismissing the sheet cancels the database request. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributingArtistsSheet(
    trackId: String,
    loadArtists: suspend () -> List<ArtistSummary>,
    onOpenArtist: (ArtistSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    var attempt by remember(trackId) { mutableIntStateOf(0) }
    val loader by rememberUpdatedState(loadArtists)
    val result by produceState<Result<List<ArtistSummary>>?>(null, trackId, attempt) {
        value = null
        value = try {
            Result.success(loader())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    val maxListHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.72f).toDp() }
    EchoModalSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        EchoSheetHeader(stringResource(R.string.player_contributing_artists))
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = maxListHeight),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            when {
                result == null -> item {
                    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }
                result?.isFailure == true -> item {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.player_artists_load_failed))
                        TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.player_artists_retry)) }
                    }
                }
                result?.getOrNull().isNullOrEmpty() -> item {
                    Text(stringResource(R.string.player_artists_empty), Modifier.padding(12.dp))
                }
                else -> items(result?.getOrNull().orEmpty(), key = { it.artistKey }) { artist ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenArtist(artist) }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        EchoArtworkImage(artist.artworkUri, null, Modifier.size(56.dp), shape = CircleShape, sizeClass = EchoArtworkSize.Thumbnail)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(artist.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (artist.trackCount > 0) pluralStringResource(R.plurals.player_artist_tracks, artist.trackCount, artist.trackCount)
                                else stringResource(R.string.feature_player_open_artist),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
