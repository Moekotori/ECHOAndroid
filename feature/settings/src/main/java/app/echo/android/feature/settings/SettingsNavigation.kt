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
import androidx.compose.ui.res.stringResource
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
    content: @Composable (SettingsCategory) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    val stateHolder = rememberSaveableStateHolder()
    val motion = rememberEchoContentMotion()
    // Pager neighbours remain composed: they must not intercept another page's back action.
    BackHandler(enabled = isActive && selected != null) { selected = null }
    // PageChrome draws its own background without providing a content color.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = { if (targetState == null) motion.pagePop() else motion.pagePush() },
            modifier = Modifier.fillMaxSize(),
            label = "settings-navigation",
        ) { category ->
            stateHolder.SaveableStateProvider(category?.name ?: "home") {
                PageChrome(
                    title = stringResource(category?.title ?: R.string.settings_title),
                    subtitle = null,
                    badgeContent = {},
                    titleContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (category != null) IconButton(onClick = { selected = null }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back), tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                stringResource(category?.title ?: R.string.settings_title),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                ) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(top = 12.dp, bottom = 172.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compactMode) 8.dp else 12.dp),
                    ) {
                        if (category == null) {
                            Text(
                                stringResource(R.string.settings_home_detail),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                            settingsGroups.forEach { (title, entries) ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        stringResource(title),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = settingsPanelColor(),
                                    ) {
                                        Column {
                                            entries.forEachIndexed { index, entry ->
                                                if (index > 0) HorizontalDivider(
                                                    modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                )
                                                SettingsCategoryRow(entry, summaries[entry].orEmpty(), compactMode) { selected = entry }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                stringResource(category.description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            content(category)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(category: SettingsCategory, summary: String, compactMode: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = if (compactMode) 8.dp else 12.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                Icon(category.icon, null, Modifier.padding(8.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(category.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
