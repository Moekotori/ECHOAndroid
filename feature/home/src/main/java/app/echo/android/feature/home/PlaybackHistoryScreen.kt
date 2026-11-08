package app.echo.android.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import app.echo.android.design.EchoIcon
import app.echo.android.model.playback.PlaybackHeatmapDay
import app.echo.android.model.playback.PlaybackHistoryEntry
import app.echo.android.model.playback.PlaybackHistoryFilter
import app.echo.android.model.playback.PlaybackHistoryGrouping
import app.echo.android.model.playback.PlaybackHistorySort
import app.echo.android.model.playback.PlaybackHistorySummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Every user intent of the history page; the host owns persistence, playback and file access. */
class PlaybackHistoryActions(
    val onFilter: ((PlaybackHistoryFilter) -> PlaybackHistoryFilter) -> Unit,
    val onReset: () -> Unit,
    val onPlay: (PlaybackHistoryEntry) -> Unit,
    val onPlayNext: (PlaybackHistoryEntry) -> Unit,
    val onEnqueue: (PlaybackHistoryEntry) -> Unit,
    val onCopy: (PlaybackHistoryEntry) -> Unit,
    val onDelete: (Long) -> Unit,
    val onClear: () -> Unit,
    val onPrune: () -> Unit,
    val onExport: () -> Unit,
    val onStats: () -> Unit,
    val onBack: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaybackHistoryScreen(
    entries: LazyPagingItems<PlaybackHistoryEntry>,
    filter: PlaybackHistoryFilter,
    summary: PlaybackHistorySummary?,
    trend: List<PlaybackHeatmapDay>,
    busy: Boolean,
    actions: PlaybackHistoryActions,
) {
    var clearConfirmation by rememberSaveable { mutableStateOf(false) }
    var pruneConfirmation by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PlaybackHistoryEntry?>(null) }
    var sheetEntry by remember { mutableStateOf<PlaybackHistoryEntry?>(null) }
    val formats = rememberHistoryFormats()

    HomeAppearance {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxSize().statusBarsPadding().imePadding()) {
                PlaybackHistoryHeader(
                    query = filter.query, busy = busy, hasHistory = summary?.plays?.let { it > 0 } ?: true,
                    onQuery = { text -> actions.onFilter { it.copy(query = text) } },
                    onExport = actions.onExport, onPrune = { pruneConfirmation = true },
                    onClear = { clearConfirmation = true }, onBack = actions.onBack,
                )
                val refresh = entries.loadState.refresh
                val listState = rememberLazyListState()
                LazyColumn(
                    Modifier.weight(1f), state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    item(key = "overview") { PlaybackHistoryOverview(summary, trend, actions.onStats) }
                    stickyHeader(key = "filters") { PlaybackHistoryFilterBar(filter, actions.onFilter) }
                    when {
                        entries.itemCount == 0 && refresh is LoadState.Loading -> item(key = "loading") {
                            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                        entries.itemCount == 0 && refresh is LoadState.Error -> item(key = "error") {
                            HistoryMessage(
                                stringResource(R.string.playback_history_load_error),
                                stringResource(R.string.playback_history_retry), entries::retry,
                            )
                        }
                        entries.itemCount == 0 -> item(key = "empty") {
                            val unfiltered = !filter.isFiltered
                            HistoryMessage(
                                stringResource(if (unfiltered) R.string.playback_history_empty else R.string.playback_history_no_results),
                                stringResource(if (unfiltered) R.string.listening_stats_empty_detail else R.string.playback_history_reset_filters),
                                if (unfiltered) null else actions.onReset,
                            )
                        }
                        else -> historyItems(entries, filter, busy, formats, actions.onPlay) { sheetEntry = it }
                    }
                    if (refresh is LoadState.Error && entries.itemCount > 0 || entries.loadState.append is LoadState.Error) item(key = "retry") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            TextButton(onClick = entries::retry) { Text(stringResource(R.string.playback_history_retry)) }
                        }
                    }
                    if (entries.loadState.append is LoadState.Loading) item(key = "appending") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                    item(key = "bottom_spacing") { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
                }
            }
        }
    }

    sheetEntry?.let { entry ->
        PlaybackHistoryActionSheet(
            entry = entry,
            subtitle = formats.subtitle(entry, filter.grouping),
            canDelete = filter.grouping == PlaybackHistoryGrouping.Timeline,
            actions = actions,
            onRequestDelete = { deleting = it },
            onDismiss = { sheetEntry = null },
        )
    }
    if (clearConfirmation) AlertDialog(
        onDismissRequest = { clearConfirmation = false },
        title = { Text(stringResource(R.string.playback_history_clear)) },
        text = { Text(stringResource(R.string.playback_history_clear_detail)) },
        confirmButton = {
            TextButton(onClick = { clearConfirmation = false; actions.onClear() }, enabled = !busy) {
                Text(stringResource(R.string.playback_history_clear))
            }
        },
        dismissButton = { TextButton(onClick = { clearConfirmation = false }) { Text(stringResource(R.string.playback_history_cancel)) } },
    )
    if (pruneConfirmation) AlertDialog(
        onDismissRequest = { pruneConfirmation = false },
        title = { Text(stringResource(R.string.playback_history_prune)) },
        text = { Text(stringResource(R.string.playback_history_prune_detail)) },
        confirmButton = {
            TextButton(onClick = { pruneConfirmation = false; actions.onPrune() }, enabled = !busy) {
                Text(stringResource(R.string.playback_history_prune_confirm))
            }
        },
        dismissButton = { TextButton(onClick = { pruneConfirmation = false }) { Text(stringResource(R.string.playback_history_cancel)) } },
    )
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.playback_history_delete)) },
            text = { Text(entry.title) },
            confirmButton = {
                TextButton(onClick = { deleting = null; actions.onDelete(entry.id) }, enabled = !busy) {
                    Text(stringResource(R.string.playback_history_delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.playback_history_cancel)) } },
        )
    }
}

/** Locale-dependent formatters, built once per locale rather than per row. */
private class HistoryFormats(
    val zone: ZoneId,
    val today: LocalDate,
    val dateFormat: DateTimeFormatter,
    val timeFormat: DateTimeFormatter,
    val weekdayFormat: DateTimeFormatter,
    val todayLabel: String,
    val yesterdayLabel: String,
) {
    fun subtitle(entry: PlaybackHistoryEntry, grouping: PlaybackHistoryGrouping): String = when (grouping) {
        PlaybackHistoryGrouping.Artists -> ""
        PlaybackHistoryGrouping.Albums -> entry.artist.trim()
        else -> listOfNotNull(entry.artist.takeIf { it.isNotBlank() }, entry.album?.takeIf { it.isNotBlank() }).joinToString(" · ")
    }
}

@Composable
private fun rememberHistoryFormats(): HistoryFormats {
    val locale = LocalConfiguration.current.locales[0]
    val todayLabel = stringResource(R.string.playback_history_today)
    val yesterdayLabel = stringResource(R.string.playback_history_yesterday)
    return remember(locale, todayLabel, yesterdayLabel) {
        val zone = ZoneId.systemDefault()
        HistoryFormats(
            zone = zone, today = LocalDate.now(zone),
            dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
            timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale),
            weekdayFormat = DateTimeFormatter.ofPattern("EEEE", locale),
            todayLabel = todayLabel, yesterdayLabel = yesterdayLabel,
        )
    }
}

/**
 * The timeline groups listens under day headers and draws a rail while the list is ordered by time;
 * any other order, and every aggregated view, is a flat ranked list where day headers would mislead.
 */
private fun LazyListScope.historyItems(
    entries: LazyPagingItems<PlaybackHistoryEntry>,
    filter: PlaybackHistoryFilter,
    busy: Boolean,
    formats: HistoryFormats,
    onPlay: (PlaybackHistoryEntry) -> Unit,
    onMore: (PlaybackHistoryEntry) -> Unit,
) {
    val timeline = filter.grouping == PlaybackHistoryGrouping.Timeline && filter.effectiveSort == PlaybackHistorySort.Recent
    val zone = formats.zone
    items(entries.itemCount, key = entries.itemKey { it.id }) { index ->
        val entry = entries[index] ?: return@items
        if (timeline) {
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
            if (firstInDay) PlaybackHistoryDateHeader(
                when (date) {
                    formats.today -> formats.todayLabel
                    formats.today.minusDays(1) -> formats.yesterdayLabel
                    else -> formats.dateFormat.format(date)
                },
                if (date == formats.today || date == formats.today.minusDays(1)) formats.dateFormat.format(date)
                else formats.weekdayFormat.format(date),
            )
            val time = remember(dateTime, formats.timeFormat) { formats.timeFormat.format(dateTime) }
            PlaybackHistoryTimelineRow(
                entry, time, firstInDay = firstInDay, lastInDay = date != nextDate, busy = busy,
                onPlay = { onPlay(entry) }, onMore = { onMore(entry) },
            )
        } else {
            val ranked = filter.effectiveSort == PlaybackHistorySort.Plays || filter.effectiveSort == PlaybackHistorySort.Time
            val top = if (filter.effectiveSort == PlaybackHistorySort.Plays && !filter.ascending) entries.peek(0)?.plays else null
            PlaybackHistoryGroupedRow(
                entry = entry,
                rank = if (ranked && filter.grouping != PlaybackHistoryGrouping.Timeline) index + 1 else null,
                share = top?.let { entry.plays.toFloat() / it.coerceAtLeast(1) },
                subtitle = formats.subtitle(entry, filter.grouping),
                busy = busy, onPlay = { onPlay(entry) }, onMore = { onMore(entry) },
            )
        }
    }
}

@Composable
private fun HistoryMessage(title: String, detail: String, action: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                EchoIcon(Icons.Rounded.History, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp).semantics { heading() },
        )
        if (action == null) Text(
            detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp).padding(top = 12.dp),
        ) else TextButton(onClick = action) { Text(detail) }
    }
}
