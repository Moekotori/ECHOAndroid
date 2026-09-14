package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.ArtistConcert
import app.echo.android.model.library.ArtistConcerts
import app.echo.android.model.library.ArtistOnlineQuery

@Composable
internal fun ArtistConcertsPage(query: ArtistOnlineQuery, active: Boolean) {
    val loader = LocalArtistOnlineInfoLoader.current
    val state = rememberArtistOnlineState<ArtistConcerts>(query to loader, active) { refresh ->
        checkNotNull(loader).loadConcerts(query, refresh)
    }
    val result = state.value
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
            items(result.events, key = { it.id }) { event -> ArtistConcertRow(event) }
        }
    }
}

@Composable
private fun ArtistConcertRow(event: ArtistConcert) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
            ArtistSourceLink(stringResource(if (event.ticketUrl != null) R.string.artist_concert_tickets else R.string.artist_concert_details, event.source),
                event.ticketUrl ?: event.url)
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = .4f))
        }
    }
}
