package app.echo.android.feature.settings

import app.echo.android.design.EchoIcon

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import app.echo.android.design.EchoPageContent
import app.echo.android.design.EchoPageEntrance
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.PageChrome

internal enum class SettingsCategory(val title: Int, val description: Int, val icon: ImageVector) {
    Appearance(R.string.settings_category_appearance, R.string.settings_category_appearance_detail, SettingsCategoryIcons.Appearance),
    Interface(R.string.settings_category_interface, R.string.settings_category_interface_detail, SettingsCategoryIcons.Interface),
    Playback(R.string.settings_category_playback, R.string.settings_category_playback_detail, SettingsCategoryIcons.Playback),
    Services(R.string.settings_category_services, R.string.settings_category_services_detail, SettingsCategoryIcons.Services),
    Library(R.string.settings_section_library, R.string.settings_category_library_detail, SettingsCategoryIcons.Library),
    About(R.string.settings_section_about, R.string.settings_category_about_detail, SettingsCategoryIcons.About),
}

@Composable
internal fun SettingsNavigation(
    isActive: Boolean,
    isPageVisible: Boolean,
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
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
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
    val wide = app.echo.android.design.LocalEchoWidthSizeClass.current == app.echo.android.model.settings.EchoWidthSizeClass.Expanded
    val home: @Composable () -> Unit = {
        SettingsHome(
            query = searchQuery, selectedCategory = selected.takeIf { wide }, compactMode = compactMode, summaries = summaries, availability = searchAvailability,
            onQueryChange = { searchQuery = it }, onOpenPlugins = onOpenPlugins,
            onOpenCategory = { searchFocus = null; selected = it },
            onSearchSelect = { result ->
                focusManager.clearFocus(); keyboardController?.hide()
                if (result.item.opensPlugins) onOpenPlugins() else {
                    searchRequestId++
                    searchFocus = SettingsSearchFocus(result.anchorTitle, searchRequestId)
                    selected = result.item.category
                }
            },
        )
    }
    if (wide) {
        SettingsStyle(compactMode) {
            EchoPageEntrance(active = isPageVisible) {
                SettingsWideNavigation(selected, home = {
                    stateHolder.SaveableStateProvider("home") { home() }
                }, detail = {
                    selected?.let { category -> stateHolder.SaveableStateProvider(category.name) {
                        SettingsDetailPane(category, compactMode, searchFocus, content)
                    } }
                })
            }
        }
        return
    }
    // PageChrome draws its own background without providing a content color.
    SettingsStyle(compactMode) {
        EchoPageEntrance(active = isPageVisible) {
            EchoPageContent(
                targetState = selected,
                contentKey = { it?.name ?: "home" },
                isBackward = { _, target -> target == null },
                modifier = Modifier.fillMaxSize().imePadding(),
                label = "settings-navigation",
                transitionSpec = {
                    val backward = targetState == null
                    val exitSpec = EchoMotion.pageFadeOut(lightweight)
                    // PageChrome is translucent: finish fading the old text before revealing
                    // the new page, including in lightweight mode and when returning home.
                    val enter = fadeIn(tween(
                        durationMillis = if (lightweight) EchoMotion.PageLightweightMs else EchoMotion.FadeMs,
                        delayMillis = exitSpec.durationMillis,
                        easing = EchoMotion.Silk,
                    ))
                    val exit = fadeOut(exitSpec)
                    ContentTransform(
                        targetContentEnter = when {
                            lightweight -> enter
                            backward -> scaleIn(
                                initialScale = EchoMotion.PageDepthScale,
                                animationSpec = EchoMotion.silkFloat(EchoMotion.PageMs),
                            ) + enter
                            else -> slideInHorizontally(EchoMotion.silkOffset(EchoMotion.PageMs)) { it } + enter
                        },
                        initialContentExit = when {
                            lightweight -> exit
                            backward -> EchoMotion.pagePop().initialContentExit + exit
                            else -> scaleOut(
                                targetScale = EchoMotion.PageDepthScale,
                                animationSpec = EchoMotion.silkFloat(EchoMotion.PageExitMs),
                            ) + exit
                        },
                        targetContentZIndex = if (backward) 0f else 1f,
                        sizeTransform = null,
                    )
                },
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
                                    EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back), tint = MaterialTheme.colorScheme.onSurface)
                                }
                                Text(
                                    stringResource(category?.title ?: R.string.settings_title),
                                    modifier = Modifier.weight(1f).semantics { heading() },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        },
                    ) {
                        if (category == null) {
                            home()
                        } else {
                            SettingsDetailPane(category, compactMode, searchFocus, content)
                        }
                    }
                }
            }
        }
    }
}
