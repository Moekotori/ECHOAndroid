package app.echo.android.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.PageChrome
import app.echo.android.design.rememberEchoContentMotion

internal enum class SettingsCategory(val title: Int, val description: Int, val icon: ImageVector) {
    Appearance(R.string.settings_category_appearance, R.string.settings_category_appearance_detail, Icons.Rounded.Palette),
    Interface(R.string.settings_category_interface, R.string.settings_category_interface_detail, Icons.Rounded.Tune),
    Playback(R.string.settings_category_playback, R.string.settings_category_playback_detail, Icons.Rounded.Headphones),
    Services(R.string.settings_category_services, R.string.settings_category_services_detail, Icons.Rounded.Devices),
    Library(R.string.settings_section_library, R.string.settings_category_library_detail, Icons.Rounded.LibraryMusic),
    About(R.string.settings_section_about, R.string.settings_category_about_detail, Icons.Rounded.Info),
}

private val settingsGroups = listOf(
    R.string.settings_group_personal to listOf(SettingsCategory.Appearance, SettingsCategory.Interface),
    R.string.settings_group_music to listOf(SettingsCategory.Playback, SettingsCategory.Services, SettingsCategory.Library),
    R.string.settings_group_app to listOf(SettingsCategory.About),
)

@Composable
internal fun SettingsNavigation(
    isActive: Boolean,
    compactMode: Boolean,
    summaries: Map<SettingsCategory, String>,
    searchAvailability: SettingsSearchAvailability,
    onOpenPlugins: () -> Unit,
    content: @Composable (SettingsCategory) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFocus by remember { mutableStateOf<SettingsSearchFocus?>(null) }
    var searchRequestId by remember { mutableIntStateOf(0) }
    val stateHolder = rememberSaveableStateHolder()
    val motion = rememberEchoContentMotion()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchItems = rememberSettingsSearchResults(searchAvailability)
    val searchResults = remember(searchItems, searchQuery) { searchSettings(searchItems, searchQuery) }
    // Pager neighbours remain composed: they must not intercept another page's back action.
    BackHandler(enabled = isActive && selected != null) {
        focusManager.clearFocus()
        keyboardController?.hide()
        selected = null
        searchFocus = null
    }
    BackHandler(enabled = isActive && selected == null && searchQuery.isNotEmpty()) {
        focusManager.clearFocus()
        keyboardController?.hide()
        searchQuery = ""
    }
    // PageChrome draws its own background without providing a content color.
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        LocalSettingsCompactMode provides compactMode,
    ) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = { if (targetState == null) motion.pagePop() else motion.pagePush() },
            modifier = Modifier.fillMaxSize().imePadding(),
            label = "settings-navigation",
        ) { category ->
            stateHolder.SaveableStateProvider(category?.name ?: "home") {
                PageChrome(
                    title = stringResource(category?.title ?: R.string.settings_title),
                    subtitle = null,
                    compactHeader = true,
                    badgeContent = {},
                    titleContent = {
                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (category != null) IconButton(onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                selected = null
                                searchFocus = null
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back), tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                stringResource(category?.title ?: R.string.settings_title),
                                modifier = Modifier.weight(1f).semantics { heading() },
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                ) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(top = 8.dp, bottom = 172.dp),
                        verticalArrangement = Arrangement.spacedBy(
                            when {
                                category != null -> if (compactMode) 12.dp else 20.dp
                                compactMode -> 12.dp
                                else -> 16.dp
                            },
                        ),
                    ) {
                        if (category == null) {
                            SettingsSearchField(searchQuery) { searchQuery = it }
                            if (searchQuery.isNotBlank()) {
                                SettingsSearchResults(searchResults) { result ->
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    if (result.item.opensPlugins) {
                                        onOpenPlugins()
                                    } else {
                                        searchRequestId++
                                        searchFocus = SettingsSearchFocus(result.anchorTitle, searchRequestId)
                                        selected = result.item.category
                                    }
                                }
                            } else settingsGroups.forEach { (title, entries) ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        stringResource(title),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 12.dp, top = 4.dp).semantics { heading() },
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = settingsPanelColor(),
                                    ) {
                                        Column {
                                            if (title == R.string.settings_group_app) {
                                                SettingsActionRow(
                                                    icon = Icons.Rounded.Extension,
                                                    title = stringResource(R.string.settings_plugins),
                                                    summary = stringResource(R.string.settings_plugins_summary),
                                                    compactMode = compactMode,
                                                    onClick = onOpenPlugins,
                                                )
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                )
                                            }
                                            entries.forEachIndexed { index, entry ->
                                                if (index > 0) HorizontalDivider(
                                                    modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                )
                                                SettingsCategoryRow(entry, summaries[entry].orEmpty(), compactMode) {
                                                    searchFocus = null
                                                    selected = entry
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            if (category != SettingsCategory.About) Text(
                                stringResource(category.description),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            CompositionLocalProvider(LocalSettingsSearchFocus provides searchFocus) {
                                content(category)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    summary: String,
    compactMode: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = if (compactMode) 6.dp else 8.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                Icon(icon, null, Modifier.padding(8.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCategoryRow(category: SettingsCategory, summary: String, compactMode: Boolean, onClick: () -> Unit) {
    SettingsActionRow(
        icon = category.icon,
        title = stringResource(category.title),
        summary = summary,
        compactMode = compactMode,
        onClick = onClick,
    )
}
