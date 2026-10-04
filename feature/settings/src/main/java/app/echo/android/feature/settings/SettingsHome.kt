package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private val settingsMenuCategories = listOf(
    SettingsCategory.Appearance,
    SettingsCategory.Playback,
    SettingsCategory.Usb,
    SettingsCategory.Interface,
    SettingsCategory.Services,
    SettingsCategory.Library,
)

/** Keep search and its index scoped to the home screen; compose only visible results. */
@Composable
internal fun SettingsHome(
    query: String,
    selectedCategory: SettingsCategory? = null,
    compactMode: Boolean,
    summaries: Map<SettingsCategory, String>,
    availability: SettingsSearchAvailability,
    onQueryChange: (String) -> Unit,
    onOpenPlugins: () -> Unit,
    onScanMusic: () -> Unit,
    onOpenConnect: () -> Unit,
    onOpenSound: () -> Unit,
    soundSummary: String,
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
            verticalArrangement = Arrangement.Top,
        ) {
            if (searching) {
                if (results.isEmpty()) item(key = "empty-search") { SettingsSearchEmpty() }
                itemsIndexed(results, key = { _, result -> "${result.item.category}:${result.item.titleRes}" },
                    contentType = { _, _ -> "search-result" }) { index, result ->
                    SettingsSearchResultRow(result, showDivider = index > 0) { onSearchSelect(result) }
                }
            } else {
                item(key = "connect", contentType = "menu-row") {
                    SettingsMenuRow(SettingsCategoryIcons.Connect,
                        stringResource(R.string.settings_quick_connect), compactMode = compactMode, onClick = onOpenConnect)
                }
                item(key = "scan", contentType = "menu-row") {
                    SettingsMenuRow(SettingsCategoryIcons.Scan,
                        stringResource(R.string.settings_quick_scan), compactMode = compactMode, onClick = onScanMusic)
                }
                item(key = "tools-divider", contentType = "divider") { SettingsMenuDivider() }
                settingsMenuCategories.forEach { entry ->
                    item(key = entry.name, contentType = "menu-row") {
                        SettingsMenuRow(entry.icon, stringResource(entry.title),
                            summary = when (entry) {
                                SettingsCategory.Appearance, SettingsCategory.Interface -> summaries[entry].orEmpty()
                                SettingsCategory.Usb -> summaries[entry].orEmpty()
                                else -> ""
                            },
                            compactMode = compactMode, selected = entry == selectedCategory) { onOpenCategory(entry) }
                    }
                    if (entry == SettingsCategory.Appearance) {
                        item(key = "sound", contentType = "menu-row") {
                            SettingsMenuRow(SettingsCategoryIcons.Sound,
                                stringResource(R.string.settings_quick_sound), soundSummary, compactMode, onClick = onOpenSound)
                        }
                    }
                }
                item(key = "plugins", contentType = "menu-row") {
                    SettingsMenuRow(SettingsCategoryIcons.Plugins,
                        stringResource(R.string.settings_plugins), compactMode = compactMode, onClick = onOpenPlugins)
                }
                item(key = "about-divider", contentType = "divider") { SettingsMenuDivider() }
                item(key = "about", contentType = "menu-row") {
                    val entry = SettingsCategory.About
                    SettingsMenuRow(entry.icon, stringResource(entry.title), summaries[entry].orEmpty(),
                        compactMode, selected = entry == selectedCategory) { onOpenCategory(entry) }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuDivider() {
    HorizontalDivider(Modifier.padding(horizontal = SettingsContentInset, vertical = 10.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
