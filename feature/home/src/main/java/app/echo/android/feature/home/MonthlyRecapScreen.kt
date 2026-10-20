package app.echo.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoIcon
import app.echo.android.model.playback.EchoMonthlyRecap
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun MonthlyRecapScreen(report: EchoMonthlyRecap?, error: Boolean, onLoad: (Int, Int) -> Unit,
    onBack: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val today = remember { YearMonth.now() }
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var month by rememberSaveable { mutableIntStateOf(today.monthValue) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    val selected = YearMonth.of(year, month)
    val current = report?.takeIf { it.year == year && it.month == month }
    LaunchedEffect(year, month) { onLoad(year, month) }
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(scheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.recap_back)) }
            Text(stringResource(R.string.recap_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { sharing = true }, enabled = current != null && !current.isEmpty) {
                EchoIcon(Icons.Rounded.Share, stringResource(R.string.recap_share))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { val next = selected.minusMonths(1); year = next.year; month = next.monthValue }, enabled = year > 1970 || month > 1) {
                EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.recap_previous))
            }
            Text(selected.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)), Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { val next = selected.plusMonths(1); year = next.year; month = next.monthValue }, enabled = selected < today) {
                EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.recap_next))
            }
        }
        when {
            error -> Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.recap_load_error))
                TextButton({ onLoad(year, month) }) { Text(stringResource(R.string.recap_retry)) }
            }
            current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            current.isEmpty -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.recap_empty)) }
            else -> MonthlyRecapContent(current)
        }
    }
    if (sharing && current != null) MonthlyRecapShareSheet(current, onDismiss = { sharing = false })
}

@Composable
private fun MonthlyRecapContent(report: EchoMonthlyRecap) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp, 12.dp, 24.dp, 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (report.partial) item { Text(stringResource(R.string.recap_partial), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceVariant.copy(alpha = .45f)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.recap_minutes_value, report.listenedMs / 60000), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.recap_totals_value, report.totalPlays, report.activeDays), color = scheme.onSurfaceVariant)
                }
            }
        }
        item { ListeningStatsHourChart(report.playsByHour) }
        item { Text(stringResource(R.string.recap_albums), style = MaterialTheme.typography.titleMedium) }
        if (report.topAlbums.isEmpty()) item { Text(stringResource(R.string.recap_no_albums), color = scheme.onSurfaceVariant) }
        itemsIndexed(report.topAlbums, key = { _, entry -> "album:${entry.key}" }) { rank, entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${rank + 1}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                ArtworkTile(entry.artworkUri, Modifier.size(52.dp), scheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(entry.title, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                    Text(entry.subtitle.orEmpty(), maxLines = 1, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Text(pluralStringResource(R.plurals.recap_plays, entry.playCount, entry.playCount), style = MaterialTheme.typography.labelMedium)
            }
        }
        item { Text(stringResource(R.string.recap_new_artists, report.newArtistCount), style = MaterialTheme.typography.titleMedium) }
        if (report.newArtists.isEmpty()) item { Text(stringResource(R.string.recap_no_new_artists), color = scheme.onSurfaceVariant) }
        itemsIndexed(report.newArtists, key = { _, entry -> "artist:${entry.key}" }) { _, entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArtworkTile(entry.artworkUri, Modifier.size(40.dp), scheme.onSurfaceVariant)
                Text(entry.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(pluralStringResource(R.plurals.recap_plays, entry.playCount, entry.playCount), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
        }
        item { Text(stringResource(R.string.recap_basis), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}
