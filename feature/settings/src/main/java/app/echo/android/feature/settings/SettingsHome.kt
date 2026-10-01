package app.echo.android.feature.settings

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val settingsGroups = listOf(
    R.string.settings_group_personal to listOf(SettingsCategory.Appearance, SettingsCategory.Interface),
    R.string.settings_group_music to listOf(SettingsCategory.Playback, SettingsCategory.Services, SettingsCategory.Library),
    R.string.settings_group_app to listOf(SettingsCategory.About),
)

/** Keep search and its index scoped to the home screen; compose only visible results. */
@Composable
internal fun SettingsHome(
    query: String,
    compactMode: Boolean,
    summaries: Map<SettingsCategory, String>,
    availability: SettingsSearchAvailability,
    onQueryChange: (String) -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenCategory: (SettingsCategory) -> Unit,
    onSearchSelect: (SettingsSearchResult) -> Unit,
) {
    val searchItems = rememberSettingsSearchResults(availability)
    val results = remember(searchItems, query) { searchSettings(searchItems, query) }
    val listState = rememberLazyListState()
    val searching = query.isNotBlank()
    var previousQuery by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (previousQuery != query) {
            listState.scrollToItem(0)
            previousQuery = query
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsSearchField(query, onQueryChange)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 172.dp),
            verticalArrangement = Arrangement.spacedBy(if (searching) 0.dp else if (compactMode) 8.dp else 12.dp),
        ) {
            if (searching) {
                if (results.isEmpty()) item(key = "empty-search") { SettingsSearchEmpty() }
                itemsIndexed(results, key = { _, result -> "${result.item.category}:${result.item.titleRes}" },
                    contentType = { _, _ -> "search-result" }) { index, result ->
                    SettingsSearchResultRow(result, showDivider = index > 0) { onSearchSelect(result) }
                }
            } else {
                settingsGroups.forEach { (title, entries) ->
                    item(key = title, contentType = "settings-group") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = SettingsContentInset).semantics { heading() },
                            )
                            Column {
                                if (title == R.string.settings_group_app) {
                                    SettingsHomeRow(SettingsCategoryIcons.Plugins,
                                        stringResource(R.string.settings_plugins),
                                        stringResource(R.string.settings_plugins_summary), compactMode, onOpenPlugins)
                                    SettingsHomeDivider()
                                }
                                entries.forEachIndexed { index, entry ->
                                    if (index > 0) SettingsHomeDivider()
                                    SettingsHomeRow(entry.icon, stringResource(entry.title),
                                        summaries[entry].orEmpty(), compactMode) { onOpenCategory(entry) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHomeDivider() {
    HorizontalDivider(Modifier.padding(start = SettingsContentInset + 34.dp, end = SettingsContentInset),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun SettingsHomeRow(icon: ImageVector, title: String, summary: String,
    compactMode: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = SettingsShape, color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = if (compactMode) 56.dp else 62.dp)
            .padding(horizontal = SettingsContentInset, vertical = if (compactMode) 6.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            EchoIcon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
