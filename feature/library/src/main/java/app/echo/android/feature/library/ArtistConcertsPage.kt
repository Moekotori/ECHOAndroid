package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.echo.android.model.library.*
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.time.Instant

@Composable
internal fun ArtistConcertsPage(query: ArtistOnlineQuery, active: Boolean) {
    val loader = LocalArtistOnlineInfoLoader.current
    var historical by rememberSaveable(query) { mutableStateOf(false) }
    val period = if (historical) ArtistConcertPeriod.Past else ArtistConcertPeriod.Upcoming
    val state = rememberArtistOnlineState<ArtistConcerts>(Triple(query, loader, period), active) { refresh ->
        checkNotNull(loader).loadConcerts(query, refresh, period)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(Instant.now(), lifecycle, active, period) {
        if (active && !historical) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { value = Instant.now(); delay(60_000L) }
        }
    }
    val result = state.value
    val events = remember(result, period, now) {
        result?.events.orEmpty().filter { (it.phase(now) == ArtistConcertPhase.Past) == historical }
    }
    var expandedId by remember(query, period) { mutableStateOf<String?>(null) }
    val scroll = remember(query, period) { LazyListState() }
    LazyColumn(Modifier.fillMaxSize(), state = scroll, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = AlbumDetailBottomPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item("heading") { ArtistOnlineHeading(stringResource(R.string.artist_concert_heading), state.loading) { state.attempt++ } }
        item("period") {
            LibraryTextTabs(listOf(stringResource(R.string.artist_upcoming_concerts), stringResource(R.string.artist_past_concerts)),
                if (historical) 1 else 0, { historical = it == 1 }, compact = true)
        }
        if (result == null) item("status") {
            ArtistOnlineNotice(state.loading || state.completedAttempt < 0, state.failed,
                stringResource(if (historical) R.string.artist_no_past_concerts else R.string.artist_no_concerts)) { state.attempt++ }
        } else {
            if (result.stale || state.failed || result.failedSources.isNotEmpty()) item("partial") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (result.stale || state.failed) Text(stringResource(R.string.artist_online_stale),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (result.failedSources.isNotEmpty()) Text(stringResource(R.string.artist_concerts_partial, result.failedSources.joinToString(" / ")),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (events.isEmpty()) item("empty") {
                Text(stringResource(if (result.failedSources.isNotEmpty()) R.string.artist_concerts_incomplete
                    else if (historical) R.string.artist_no_past_concerts else R.string.artist_no_concerts),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(events, key = { it.id }) { event ->
                ArtistConcertRow(query, event, now, active, historical, expandedId == event.id,
                    onToggle = { expandedId = if (expandedId == event.id) null else event.id })
            }
            result.fetchedAt?.let { fetched -> item("checked") {
                Text(stringResource(R.string.artist_concert_checked, concertDisplayStamp(fetched, null)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
        if (events.isEmpty() && !state.loading && state.completedAttempt >= 0) item("sources") {
            val encoded = remember(query.name) { URLEncoder.encode(query.name, "UTF-8") }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArtistSourceLink(stringResource(R.string.artist_concert_check_source, "Songkick"), "https://www.songkick.com/search?query=$encoded")
                ArtistSourceLink(stringResource(R.string.artist_concert_check_source, "Eventernote"), "https://www.eventernote.com/events/search?keyword=$encoded")
                if (historical) Text(stringResource(R.string.artist_concert_history_note),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
