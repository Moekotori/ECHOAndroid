package app.echo.android.feature.home

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.ListeningStatsRange

@Composable
internal fun PlaybackHistoryHeader(
    query: String,
    range: ListeningStatsRange,
    busy: Boolean,
    onQuery: (String) -> Unit,
    onRange: (ListeningStatsRange) -> Unit,
    onClear: () -> Unit,
    onStats: () -> Unit,
    onBack: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    val searching = searchExpanded || query.isNotEmpty()
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(searching) {
        if (searching) searchFocus.requestFocus()
    }

    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (searching) {
                focusManager.clearFocus()
                keyboard?.hide()
                onQuery("")
                searchExpanded = false
            } else onBack()
        }) {
            EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(
                if (searching) R.string.playback_history_cancel else R.string.feature_home_back_49093c))
        }
        if (searching) {
            app.echo.android.design.EchoSearchField(query, onQuery,
                stringResource(R.string.playback_history_search), stringResource(R.string.playback_history_reset_search),
                Modifier.weight(1f).focusRequester(searchFocus))
        } else {
            Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.playback_history_title), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.playback_history_subtitle), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { searchExpanded = true }) {
                EchoIcon(Icons.Rounded.Search, stringResource(R.string.playback_history_search))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    EchoIcon(Icons.Rounded.MoreHoriz, stringResource(R.string.playback_history_more))
                }
                DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(16.dp), containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.playback_history_clear)) }, enabled = !busy,
                        leadingIcon = { EchoIcon(Icons.Rounded.DeleteOutline, null) },
                        onClick = { menuOpen = false; onClear() })
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(ListeningStatsRange.All, ListeningStatsRange.Week, ListeningStatsRange.Month, ListeningStatsRange.Year).forEach { option ->
                val active = range == option
                val accent = MaterialTheme.colorScheme.primary
                Surface(onClick = { onRange(option) }, shape = RoundedCornerShape(12.dp),
                    color = if (active) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f) else Color.Transparent,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.Tab; selected = active }) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(when (option) {
                            ListeningStatsRange.Week -> R.string.listening_stats_range_week
                            ListeningStatsRange.Month -> R.string.listening_stats_range_month
                            ListeningStatsRange.Year -> R.string.listening_stats_range_year
                            ListeningStatsRange.All -> R.string.listening_stats_range_all
                        }), style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onStats) {
            EchoIcon(Icons.Rounded.BarChart, stringResource(R.string.listening_stats_title), Modifier.size(22.dp))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
internal fun PlaybackHistoryDateHeader(title: String, detail: String) {
    Surface(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp).semantics { heading() },
        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.widthIn(max = 144.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
