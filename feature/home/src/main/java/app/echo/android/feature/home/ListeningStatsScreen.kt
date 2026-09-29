package app.echo.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.ListeningStats
import app.echo.android.model.playback.ListeningStatsEntry
import app.echo.android.model.playback.ListeningStatsRange

/**
 * 听歌统计页。数据由调用方按范围加载；本页只负责展示和切换范围。
 * [onOpenTrack] 以 trackId 回调，曲目可能已不在曲库里，由调用方决定如何处理。
 */
@Composable
fun ListeningStatsScreen(
    stats: ListeningStats?,
    onLoad: (ListeningStatsRange) -> Unit,
    onOpenTrack: (trackId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var range by rememberSaveable { mutableStateOf(ListeningStatsRange.Month) }
    LaunchedEffect(range) { onLoad(range) }
    val current = stats?.takeIf { it.range == range }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.feature_home_back_49093c),
                    tint = homeBodyColor(),
                )
            }
            Text(
                stringResource(R.string.listening_stats_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ListeningStatsRange.entries.forEach { option ->
                FilterChip(
                    selected = option == range,
                    onClick = { range = option },
                    label = { Text(option.label()) },
                )
            }
        }
        when {
            current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            current.isEmpty -> ListeningStatsEmpty()
            else -> ListeningStatsContent(current, onOpenTrack)
        }
    }
}

@Composable
private fun ListeningStatsContent(stats: ListeningStats, onOpenTrack: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "totals") { ListeningStatsTotals(stats) }
        item(key = "hours") { ListeningStatsHourChart(stats.playsByHour, Modifier.padding(top = 12.dp)) }
        rankSection("tracks", R.string.listening_stats_top_tracks, stats.topTracks, circle = false) { onOpenTrack(it.key) }
        rankSection("albums", R.string.listening_stats_top_albums, stats.topAlbums, circle = false, onClick = null)
        rankSection("artists", R.string.listening_stats_top_artists, stats.topArtists, circle = true, onClick = null)
        item(key = "bottom") { Box(Modifier.navigationBarsPadding()) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.rankSection(
    key: String,
    titleRes: Int,
    entries: List<ListeningStatsEntry>,
    circle: Boolean,
    onClick: ((ListeningStatsEntry) -> Unit)?,
) {
    if (entries.isEmpty()) return
    item(key = "$key-title") {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
    itemsIndexed(entries, key = { _, entry -> "$key:${entry.key}" }) { index, entry ->
        ListeningStatsRankRow(rank = index + 1, entry = entry, circle = circle, onClick = onClick?.let { handler -> { handler(entry) } })
    }
}

@Composable
private fun ListeningStatsTotals(stats: ListeningStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        ListeningStatsFigure(
            label = stringResource(R.string.listening_stats_total_time),
            value = formatListeningDuration(stats.totalListenedMs),
            modifier = Modifier.weight(1.4f),
        )
        ListeningStatsFigure(
            label = stringResource(R.string.listening_stats_total_plays),
            value = stats.totalPlays.toString(),
            modifier = Modifier.weight(1f),
        )
        ListeningStatsFigure(
            label = stringResource(R.string.listening_stats_active_days),
            value = stats.activeDays.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ListeningStatsFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = homeBodyColor())
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
        )
    }
}

@Composable
private fun ListeningStatsEmpty() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.listening_stats_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.listening_stats_empty_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = homeBodyColor(),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ListeningStatsRange.label(): String = stringResource(
    when (this) {
        ListeningStatsRange.Week -> R.string.listening_stats_range_week
        ListeningStatsRange.Month -> R.string.listening_stats_range_month
        ListeningStatsRange.Year -> R.string.listening_stats_range_year
        ListeningStatsRange.All -> R.string.listening_stats_range_all
    },
)

@Composable
internal fun formatListeningDuration(ms: Long): String {
    val totalMinutes = (ms / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.listening_stats_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.listening_stats_minutes, minutes)
    }
}

@Composable
internal fun listeningPlayCount(count: Int): String =
    pluralStringResource(R.plurals.listening_stats_play_count, count, count)
