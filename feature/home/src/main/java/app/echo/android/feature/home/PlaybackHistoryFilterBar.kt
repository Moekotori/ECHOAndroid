package app.echo.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoDropdownMenuDivider
import app.echo.android.design.EchoDropdownMenuItem
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable
import app.echo.android.model.playback.PlaybackHistoryFilter
import app.echo.android.model.playback.PlaybackHistoryGrouping
import app.echo.android.model.playback.PlaybackHistoryPeriod
import app.echo.android.model.playback.PlaybackHistorySort
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Sticky strip under the overview: how to group, which period, and how to sort. */
@Composable
internal fun PlaybackHistoryFilterBar(
    filter: PlaybackHistoryFilter,
    onFilter: ((PlaybackHistoryFilter) -> PlaybackHistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picker by rememberSaveable { mutableStateOf<HistoryDatePicker?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val shortFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale) }
    val from = filter.fromEpochDay
    val to = filter.toEpochDay
    val period = filter.period
    val selectedPeriodLabel = when {
        period == PlaybackHistoryPeriod.Day && from != null ->
            dateFormat.format(LocalDate.ofEpochDay(from))
        period == PlaybackHistoryPeriod.Custom && from != null && to != null ->
            "${shortFormat.format(LocalDate.ofEpochDay(from))} – ${shortFormat.format(LocalDate.ofEpochDay(to))}"
        else -> stringResource(periodLabel(period))
    }

    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlaybackHistoryGrouping.entries.forEach { grouping ->
                HistoryGroupingTab(stringResource(groupingLabel(grouping)), filter.grouping == grouping) {
                    onFilter {
                        val sort = if (it.sort.isAvailableFor(grouping)) it.sort else PlaybackHistorySort.Recent
                        it.copy(grouping = grouping, sort = sort)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PeriodButton(selectedPeriodLabel, period, Modifier.weight(1f)) { selection ->
                when (selection) {
                    PlaybackHistoryPeriod.Day -> picker = HistoryDatePicker.Day
                    PlaybackHistoryPeriod.Custom -> picker = HistoryDatePicker.Range
                    else -> onFilter { it.copy(period = selection, fromEpochDay = null, toEpochDay = null) }
                }
            }
            SortButton(filter, onFilter)
        }
        HorizontalDivider()
    }

    when (picker) {
        HistoryDatePicker.Day -> HistoryDayPickerDialog(
            initialEpochDay = filter.fromEpochDay,
            onDismiss = { picker = null },
            onPick = { day ->
                picker = null
                onFilter { it.copy(period = PlaybackHistoryPeriod.Day, fromEpochDay = day, toEpochDay = day) }
            },
        )
        HistoryDatePicker.Range -> HistoryRangePickerDialog(
            initialFrom = filter.fromEpochDay, initialTo = filter.toEpochDay,
            onDismiss = { picker = null },
            onPick = { from, to ->
                picker = null
                onFilter { it.copy(period = PlaybackHistoryPeriod.Custom, fromEpochDay = from, toEpochDay = to) }
            },
        )
        null -> Unit
    }
}

private enum class HistoryDatePicker { Day, Range }

@Composable
private fun PeriodButton(
    label: String,
    selectedPeriod: PlaybackHistoryPeriod,
    modifier: Modifier = Modifier,
    onSelect: (PlaybackHistoryPeriod) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).echoClickable(role = Role.Button) { open = true }
                .heightIn(min = 48.dp).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoIcon(Icons.Rounded.CalendarToday, null, Modifier.size(16.dp))
            Text(
                label, style = MaterialTheme.typography.labelLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            EchoIcon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp))
        }
        EchoDropdownMenu(open, onDismissRequest = { open = false }) {
            PlaybackHistoryPeriod.entries.forEach { period ->
                EchoDropdownMenuItem(
                    text = { Text(stringResource(periodLabel(period))) },
                    selected = period == selectedPeriod,
                    onClick = {
                        open = false
                        onSelect(period)
                    },
                )
            }
        }
    }
}

@Composable
private fun SortButton(
    filter: PlaybackHistoryFilter,
    onFilter: ((PlaybackHistoryFilter) -> PlaybackHistoryFilter) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            EchoIcon(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.playback_history_sort), Modifier.size(22.dp))
        }
        EchoDropdownMenu(open, onDismissRequest = { open = false }) {
            PlaybackHistorySort.entries.filter { it.isAvailableFor(filter.grouping) }.forEach { sort ->
                EchoDropdownMenuItem(
                    text = { Text(stringResource(sortLabel(sort))) },
                    selected = filter.effectiveSort == sort,
                    onClick = { open = false; onFilter { it.copy(sort = sort) } },
                )
            }
            EchoDropdownMenuDivider()
            EchoDropdownMenuItem(
                text = { Text(stringResource(R.string.playback_history_sort_descending)) },
                selected = !filter.ascending,
                leadingIcon = { EchoIcon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp)) },
                onClick = { open = false; onFilter { it.copy(ascending = false) } },
            )
            EchoDropdownMenuItem(
                text = { Text(stringResource(R.string.playback_history_sort_ascending)) },
                selected = filter.ascending,
                leadingIcon = { EchoIcon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp)) },
                onClick = { open = false; onFilter { it.copy(ascending = true) } },
            )
        }
    }
}

@Composable
private fun HistoryGroupingTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
            .drawBehind {
                if (selected) {
                    val stroke = 2.dp.toPx()
                    val inset = 8.dp.toPx()
                    val y = size.height - stroke / 2f
                    drawLine(scheme.primary, Offset(inset, y), Offset(size.width - inset, y), stroke)
                }
            }
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun groupingLabel(grouping: PlaybackHistoryGrouping): Int = when (grouping) {
    PlaybackHistoryGrouping.Timeline -> R.string.playback_history_group_timeline
    PlaybackHistoryGrouping.Tracks -> R.string.playback_history_group_tracks
    PlaybackHistoryGrouping.Albums -> R.string.playback_history_group_albums
    PlaybackHistoryGrouping.Artists -> R.string.playback_history_group_artists
}

private fun periodLabel(period: PlaybackHistoryPeriod): Int = when (period) {
    PlaybackHistoryPeriod.All -> R.string.listening_stats_range_all
    PlaybackHistoryPeriod.Today -> R.string.playback_history_today
    PlaybackHistoryPeriod.Week -> R.string.playback_history_period_week
    PlaybackHistoryPeriod.Month -> R.string.playback_history_period_month
    PlaybackHistoryPeriod.Year -> R.string.playback_history_period_year
    PlaybackHistoryPeriod.Day -> R.string.playback_history_period_day
    PlaybackHistoryPeriod.Custom -> R.string.playback_history_period_custom
}

private fun sortLabel(sort: PlaybackHistorySort): Int = when (sort) {
    PlaybackHistorySort.Recent -> R.string.playback_history_sort_recent
    PlaybackHistorySort.Plays -> R.string.listening_stats_total_plays
    PlaybackHistorySort.Time -> R.string.listening_stats_total_time
    PlaybackHistorySort.Title -> R.string.playback_history_sort_title
    PlaybackHistorySort.Artist -> R.string.playback_history_sort_artist
}
