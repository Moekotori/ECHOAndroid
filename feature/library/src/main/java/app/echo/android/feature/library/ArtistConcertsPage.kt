package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.ArtistConcert
import app.echo.android.model.library.ArtistConcerts
import app.echo.android.model.library.ArtistOnlineQuery
import app.echo.android.model.library.ArtistSetlist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ArtistConcertsPage(query: ArtistOnlineQuery, active: Boolean) {
    val loader = LocalArtistOnlineInfoLoader.current
    val matcher = LocalArtistSetlistMatcher.current
    val player = LocalArtistSetlistPlayer.current
    val state = rememberArtistOnlineState<ArtistConcerts>(query to loader, active) { refresh ->
        checkNotNull(loader).loadConcerts(query, refresh)
    }
    val result = state.value
    var expandedId by remember(query) { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = AlbumDetailBottomPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item("heading") { ArtistOnlineHeading(stringResource(R.string.artist_upcoming_concerts), state.loading) { state.attempt++ } }
        if (result == null) item("status") {
            ArtistOnlineNotice(state.loading || state.completedAttempt < 0, state.failed, stringResource(R.string.artist_no_concerts)) { state.attempt++ }
        } else {
            if (result.stale || state.failed || result.failedSources.isNotEmpty()) item("partial") {
                Text(stringResource(if (result.stale || state.failed) R.string.artist_online_stale else R.string.artist_concerts_partial,
                    result.failedSources.joinToString(" / ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (result.events.isEmpty()) item("empty") {
                Text(stringResource(if (result.failedSources.isEmpty()) R.string.artist_no_concerts else R.string.artist_concerts_incomplete),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(result.events, key = { it.id }) { event ->
                ArtistConcertRow(
                    event = event,
                    expanded = expandedId == event.id,
                    onToggle = { expandedId = if (expandedId == event.id) null else event.id },
                    query = query,
                    loader = loader,
                    matcher = matcher,
                    onPlayMatched = player,
                )
            }
        }
    }
}

@Composable
private fun ArtistConcertRow(
    event: ArtistConcert,
    expanded: Boolean,
    onToggle: () -> Unit,
    query: ArtistOnlineQuery,
    loader: app.echo.android.model.library.ArtistOnlineInfoLoader?,
    matcher: app.echo.android.model.library.ArtistSetlistMatcher?,
    onPlayMatched: ((List<String>) -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.width(64.dp).background(colors.surfaceContainerHigh, RoundedCornerShape(16.dp)).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(event.date.take(4), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(event.date.substring(5).replace('-', '/'), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val location = listOfNotNull(event.city, event.venue).distinct().joinToString(" · ")
                if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                event.time?.let { Text(stringResource(R.string.artist_concert_local_time, it), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                Text(
                    stringResource(if (expanded) R.string.artist_setlist_hide else R.string.artist_setlist_show),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
                ArtistSourceLink(stringResource(if (event.ticketUrl != null) R.string.artist_concert_tickets else R.string.artist_concert_details, event.source),
                    event.ticketUrl ?: event.url)
            }
        }
        if (expanded) {
            ConcertSetlistSection(query, event, loader, matcher, onPlayMatched)
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = .4f))
    }
}

@Composable
private fun ConcertSetlistSection(
    query: ArtistOnlineQuery,
    event: ArtistConcert,
    loader: app.echo.android.model.library.ArtistOnlineInfoLoader?,
    matcher: app.echo.android.model.library.ArtistSetlistMatcher?,
    onPlayMatched: ((List<String>) -> Unit)?,
) {
    var setlist by remember(event.id) { mutableStateOf<ArtistSetlist?>(null) }
    var loading by remember(event.id) { mutableStateOf(true) }
    var failed by remember(event.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(event.id, loader, matcher) {
        if (loader == null) {
            loading = false
            failed = true
            return@LaunchedEffect
        }
        loading = true
        failed = false
        try {
            val remote = loader.loadSetlist(query, event, false)
            setlist = matcher?.match(remote) ?: remote
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    val current = setlist
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            failed -> Text(stringResource(R.string.artist_setlist_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            current?.missingApiKey == true -> Text(stringResource(R.string.artist_setlist_need_key), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            current == null || current.songs.isEmpty() -> Text(stringResource(R.string.artist_setlist_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                if (!current.exactDate) {
                    Text(stringResource(R.string.artist_setlist_recent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (current.stale) {
                    Text(stringResource(R.string.artist_online_stale, "setlist.fm"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val playable = remember(current) { playableSetlistTrackIds(current.songs) }
                if (playable.isNotEmpty() && onPlayMatched != null) {
                    Button(onClick = { onPlayMatched(playable) }) {
                        Text(stringResource(R.string.artist_setlist_play_matched, playable.size, current.songs.size))
                    }
                }
                current.songs.forEach { song ->
                    val matched = song.trackId != null
                    Text(
                        text = if (song.tape) stringResource(R.string.artist_setlist_tape, song.title) else song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (matched) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = if (matched && onPlayMatched != null) {
                            Modifier.clickable { onPlayMatched(listOf(song.trackId!!)) }
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
        TextButton(onClick = {
            if (loader == null) return@TextButton
            scope.launch {
                loading = true
                failed = false
                try {
                    val remote = loader.loadSetlist(query, event, true)
                    setlist = matcher?.match(remote) ?: remote
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                } finally {
                    loading = false
                }
            }
        }) { Text(stringResource(R.string.artist_setlist_refresh)) }
    }
}
