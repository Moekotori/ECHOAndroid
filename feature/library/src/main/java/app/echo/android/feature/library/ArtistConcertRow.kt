package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*
import java.time.Instant

@Composable
internal fun ArtistConcertRow(query: ArtistOnlineQuery, event: ArtistConcert, now: Instant, active: Boolean,
    historical: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var showSetlist by remember(query, event.id) { mutableStateOf(false) }
    val detailLabel = stringResource(if (expanded) R.string.artist_concert_hide_details else R.string.artist_concert_show_details)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().semantics { stateDescription = detailLabel }
            .clickable(role = Role.Button, onClickLabel = detailLabel, onClick = onToggle),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(64.dp).background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(event.date.take(4), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(event.date.drop(5).replace('-', '/'), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = colors.primary, modifier = Modifier.padding(top = 4.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                val location = remember(event.city, event.venue) { listOfNotNull(event.city, event.venue).distinct().joinToString(" · ") }
                if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Text(event.time?.let { stringResource(R.string.artist_concert_local_time, it) }
                    ?: stringResource(R.string.artist_concert_time_tbd), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                val phase = event.phase(now)
                if (phase == ArtistConcertPhase.Ongoing || phase == ArtistConcertPhase.Started) Text(
                    stringResource(if (phase == ArtistConcertPhase.Ongoing) R.string.artist_concert_ongoing else R.string.artist_concert_started),
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(detailLabel, style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
        }
        if (expanded) ArtistConcertDetails(event)
        Row(Modifier.fillMaxWidth().padding(start = 80.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showSetlist = !showSetlist }) {
                Text(stringResource(if (showSetlist) R.string.artist_setlist_hide else R.string.artist_setlist_show))
            }
        }
        val link = if (historical) event.url else event.ticketUrl ?: event.url
        if (link.isNotBlank()) Box(Modifier.padding(start = 80.dp)) {
            ArtistSourceLink(stringResource(if (!historical && event.ticketUrl != null) R.string.artist_concert_tickets
                else R.string.artist_concert_details, event.source), link)
        }
        if (showSetlist && active) ConcertSetlistSection(query, event, LocalArtistOnlineInfoLoader.current,
            LocalArtistSetlistMatcher.current, LocalArtistSetlistPlayer.current)
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = .4f))
    }
}
