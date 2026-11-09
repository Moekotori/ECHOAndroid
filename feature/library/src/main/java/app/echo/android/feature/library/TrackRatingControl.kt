package app.echo.android.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun EchoTrack.supportsLocalRating(): Boolean =
    source == LibrarySource.MediaStore || source == LibrarySource.Saf

@Composable
internal fun TrackRatingRow(rating: Int, onClick: () -> Unit) {
    val ratingDescription = if (rating > 0) {
        stringResource(R.string.track_rating_stars, rating)
    } else {
        stringResource(R.string.track_rating_unrated)
    }
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth()
            .echoClickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .semantics { stateDescription = ratingDescription },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.track_rating), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (star in 1..5) {
                EchoIcon(
                    if (star <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = tint,
                )
            }
        }
        EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = tint)
    }
}

@Composable
internal fun TrackRatingControl(
    track: EchoTrack,
    set: suspend (EchoTrack, Int?) -> Int,
    onDismiss: () -> Unit,
) {
    if (!track.supportsLocalRating()) return
    var value by remember(track.id, track.rating) { mutableIntStateOf(track.rating) }
    var busy by remember(track.id) { mutableStateOf(false) }
    var failed by remember(track.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun choose(rating: Int?) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try {
                value = set(track, rating)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.track_rating)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { choose(star) }, enabled = !busy) {
                            EchoIcon(
                                if (star <= value) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                stringResource(R.string.track_rating_stars, star),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                FlowRow {
                    TextButton(onClick = { choose(0) }, enabled = !busy) { Text(stringResource(R.string.track_rating_unrated)) }
                    TextButton(onClick = { choose(null) }, enabled = !busy) { Text(stringResource(R.string.track_rating_file)) }
                }
                if (failed) Text(stringResource(R.string.playlist_action_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.feature_library_close_473a69))
            }
        },
    )
}

@Composable
internal fun MinimumRatingControl(value: Int, onChange: (Int) -> Unit) {
    Text(stringResource(R.string.track_rating_minimum), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (0..5).forEach { rating -> FilterChip(value == rating, { onChange(rating) }, label = {
            Text(if (rating == 0) stringResource(R.string.library_filter_any) else stringResource(R.string.track_rating_at_least, rating))
        }) }
    }
}
