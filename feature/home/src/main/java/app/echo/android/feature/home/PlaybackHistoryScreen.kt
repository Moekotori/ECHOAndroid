package app.echo.android.feature.home

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import app.echo.android.model.playback.ListeningStatsRange
import app.echo.android.model.playback.PlaybackHistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun PlaybackHistoryScreen(
    entries: LazyPagingItems<PlaybackHistoryEntry>,
    query: String,
    range: ListeningStatsRange,
    busy: Boolean,
    onQuery: (String) -> Unit,
    onRange: (ListeningStatsRange) -> Unit,
    onPlay: (PlaybackHistoryEntry) -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
    onStats: () -> Unit,
    onBack: () -> Unit,
) {
    var clearConfirmation by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PlaybackHistoryEntry?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val timeFormat = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val todayLabel = stringResource(R.string.playback_history_today)
    val yesterdayLabel = stringResource(R.string.playback_history_yesterday)
    val weekdayFormat = remember(locale) { DateTimeFormatter.ofPattern("EEEE", locale) }

    HomeAppearance {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize().statusBarsPadding().imePadding()) {
                PlaybackHistoryHeader(query, range, busy, onQuery, onRange,
                    onClear = { clearConfirmation = true }, onStats = onStats, onBack = onBack)
                val refresh = entries.loadState.refresh
                when {
                    entries.itemCount == 0 && refresh is LoadState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    entries.itemCount == 0 && refresh is LoadState.Error -> HistoryMessage(
                        stringResource(R.string.playback_history_load_error), stringResource(R.string.playback_history_retry), entries::retry)
                    entries.itemCount == 0 -> HistoryMessage(
                        stringResource(if (query.isBlank() && range == ListeningStatsRange.All) R.string.playback_history_empty
                            else R.string.playback_history_no_results),
                        stringResource(if (query.isBlank() && range == ListeningStatsRange.All) R.string.listening_stats_empty_detail
                            else R.string.playback_history_reset_filters),
                        if (query.isBlank() && range == ListeningStatsRange.All) null else ({ onQuery(""); onRange(ListeningStatsRange.All) }))
                    else -> key(query, range) {
                        val listState = rememberLazyListState()
                        LazyColumn(Modifier.weight(1f), state = listState,
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                            items(entries.itemCount, key = entries.itemKey { it.id }) { index ->
                                val entry = entries[index]
                                if (entry != null) {
                                    val dateTime = remember(entry.playedAtEpochMs, zone) { Instant.ofEpochMilli(entry.playedAtEpochMs).atZone(zone) }
                                    val date = dateTime.toLocalDate()
                                    val previous = if (index > 0) entries.peek(index - 1) else null
                                    val next = if (index + 1 < entries.itemCount) entries.peek(index + 1) else null
                                    val previousDate = remember(previous?.playedAtEpochMs, zone) {
                                        previous?.let { Instant.ofEpochMilli(it.playedAtEpochMs).atZone(zone).toLocalDate() }
                                    }
                                    val nextDate = remember(next?.playedAtEpochMs, zone) {
                                        next?.let { Instant.ofEpochMilli(it.playedAtEpochMs).atZone(zone).toLocalDate() }
                                    }
                                    val firstInDay = date != previousDate
                                    if (firstInDay) PlaybackHistoryDateHeader(when (date) {
                                        today -> todayLabel
                                        today.minusDays(1) -> yesterdayLabel
                                        else -> dateFormat.format(date)
                                    }, if (date == today || date == today.minusDays(1)) dateFormat.format(date) else weekdayFormat.format(date))
                                    val time = remember(dateTime, timeFormat) { timeFormat.format(dateTime) }
                                    PlaybackHistoryRow(entry, time, firstInDay = firstInDay, lastInDay = date != nextDate,
                                        busy = busy, onPlay = { onPlay(entry) },
                                        onDelete = { if (!busy) deleting = entry })
                                }
                            }
                            if (refresh is LoadState.Error || entries.loadState.append is LoadState.Error) item(key = "retry") {
                                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = entries::retry) { Text(stringResource(R.string.playback_history_retry)) }
                                }
                            }
                            if (entries.loadState.append is LoadState.Loading) item(key = "loading") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                }
                            }
                            item(key = "bottom_spacing") { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
    if (clearConfirmation) AlertDialog(onDismissRequest = { clearConfirmation = false },
        title = { Text(stringResource(R.string.playback_history_clear)) },
        text = { Text(stringResource(R.string.playback_history_clear_detail)) },
        confirmButton = { TextButton(onClick = { clearConfirmation = false; onClear() }, enabled = !busy) {
            Text(stringResource(R.string.playback_history_clear))
        } }, dismissButton = { TextButton(onClick = { clearConfirmation = false }) { Text(stringResource(R.string.playback_history_cancel)) } })
    deleting?.let { entry -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text(stringResource(R.string.playback_history_delete)) }, text = { Text(entry.title) },
        confirmButton = { TextButton(onClick = { deleting = null; onDelete(entry.id) }, enabled = !busy) {
            Text(stringResource(R.string.playback_history_delete))
        } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.playback_history_cancel)) } }) }
}

@Composable
private fun ColumnScope.HistoryMessage(title: String, detail: String, action: (() -> Unit)?) {
    Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                EchoIcon(Icons.Rounded.History, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp))
        if (action == null) Text(detail, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp).padding(top = 12.dp))
        else TextButton(onClick = action) { Text(detail) }
    }
}
