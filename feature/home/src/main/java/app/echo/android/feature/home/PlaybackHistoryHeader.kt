package app.echo.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.ListeningStatsRange

@Composable
internal fun PlaybackHistoryHeader(
    query: String,
    range: ListeningStatsRange,
    busy: Boolean,
    compact: Boolean,
    onQuery: (String) -> Unit,
    onRange: (ListeningStatsRange) -> Unit,
    onClear: () -> Unit,
    onStats: () -> Unit,
    onBack: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.feature_home_back_49093c))
        }
        if (compact) Text(stringResource(R.string.playback_history_title), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        TextButton(onClick = onStats) {
            Icon(Icons.Rounded.BarChart, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.listening_stats_title), style = MaterialTheme.typography.labelLarge)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.playback_history_more))
            }
            DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.playback_history_clear)) }, enabled = !busy,
                    onClick = { menuOpen = false; onClear() })
            }
        }
    }
    if (!compact) Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)) {
        Text(stringResource(R.string.playback_history_title), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.playback_history_subtitle), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
    app.echo.android.design.EchoSearchField(query, onQuery,
        stringResource(R.string.playback_history_search), stringResource(R.string.playback_history_reset_search),
        Modifier.padding(horizontal = 24.dp))
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        listOf(ListeningStatsRange.All, ListeningStatsRange.Week, ListeningStatsRange.Month, ListeningStatsRange.Year).forEach { option ->
            val active = range == option
            val accent = MaterialTheme.colorScheme.primary
            Surface(onClick = { onRange(option) }, shape = RectangleShape, color = Color.Transparent,
                modifier = Modifier.semantics { role = Role.Tab; selected = active }) {
                Text(stringResource(when (option) {
                    ListeningStatsRange.Week -> R.string.listening_stats_range_week
                    ListeningStatsRange.Month -> R.string.listening_stats_range_month
                    ListeningStatsRange.Year -> R.string.listening_stats_range_year
                    ListeningStatsRange.All -> R.string.listening_stats_range_all
                }), style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.drawWithCache {
                        val inset = 12.dp.toPx()
                        val thickness = 2.dp.toPx()
                        onDrawBehind {
                            if (active) drawLine(accent, androidx.compose.ui.geometry.Offset(inset, size.height - thickness / 2),
                                androidx.compose.ui.geometry.Offset(size.width - inset, size.height - thickness / 2), thickness)
                        }
                    }.padding(horizontal = 12.dp, vertical = 16.dp))
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
internal fun PlaybackHistoryDateHeader(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
        Spacer(Modifier.width(12.dp))
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
