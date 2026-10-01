package app.echo.android.feature.plugins

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import app.echo.android.plugin.PluginCapability
import app.echo.android.plugin.PluginInstallResult
import app.echo.android.plugin.PluginsSnapshot
import app.echo.android.plugin.PluginSummary
import app.echo.android.design.EchoPageContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

private enum class PluginDestination(val depth: Int) {
    List(0),
    Detail(1),
    Tutorial(1),
    Page(2),
}

// Retain the outgoing plugin during exit even if selectedId has already changed.
private data class PluginPageTarget(val destination: PluginDestination, val plugin: PluginSummary?) {
    val key: String get() = when (destination) {
        PluginDestination.List, PluginDestination.Tutorial -> destination.name
        else -> "${destination.name}:${plugin?.id}"
    }
}

@Composable
fun PluginsHost(
    snapshot: PluginsSnapshot,
    notice: PluginInstallResult?,
    onImport: () -> Unit,
    onInstallSample: () -> Unit,
    onBack: () -> Unit,
    onEnable: (String, Boolean) -> Unit,
    onGrant: (String, PluginCapability, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onOpenPage: (String) -> Unit,
    onAction: (String, String) -> Unit,
    onDismissNotice: () -> Unit,
    focusPluginId: String? = null,
    onFocusPluginConsumed: () -> Unit = {},
    isActive: Boolean = true,
) {
    var destinationName by rememberSaveable { mutableStateOf(PluginDestination.List.name) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val destination = PluginDestination.valueOf(destinationName)
    fun show(next: PluginDestination) {
        destinationName = next.name
    }
    val selected = snapshot.plugins.firstOrNull { it.id == selectedId }
    val pageState = rememberSaveableStateHolder()
    var retainedPluginId by remember { mutableStateOf(selectedId) }
    LaunchedEffect(selectedId) {
        // Bound retained scroll/form state to the list, tutorial and one selected plugin.
        retainedPluginId?.takeIf { it != selectedId }?.let { previous ->
            pageState.removeState("Detail:$previous")
            pageState.removeState("Page:$previous")
        }
        retainedPluginId = selectedId
    }
    LaunchedEffect(focusPluginId) {
        val id = focusPluginId ?: return@LaunchedEffect
        selectedId = id
        show(PluginDestination.Detail)
        onFocusPluginConsumed()
    }
    LaunchedEffect(selectedId, snapshot) {
        val id = selectedId ?: return@LaunchedEffect
        if (!snapshot.loaded || snapshot.plugins.any { it.id == id }) return@LaunchedEffect
        delay(500)
        if (selectedId == id) {
            selectedId = null
            show(PluginDestination.List)
        }
    }
    BackHandler(enabled = isActive) {
        when (destination) {
            PluginDestination.List -> onBack()
            PluginDestination.Page -> show(PluginDestination.Detail)
            else -> show(PluginDestination.List)
        }
    }
    EchoPageContent(
        targetState = PluginPageTarget(destination, selected),
        contentKey = { it.key },
        isBackward = { initial, target -> target.destination.depth < initial.destination.depth },
        modifier = Modifier.fillMaxSize(),
        label = "plugin-navigation",
    ) { target ->
        pageState.SaveableStateProvider(target.key) {
            when (target.destination) {
                PluginDestination.List -> PluginsScreen(
                    snapshot = snapshot,
                    notice = notice,
                    onImport = onImport,
                    onOpenTutorial = { show(PluginDestination.Tutorial) },
                    onOpenPlugin = { id ->
                        selectedId = id
                        show(PluginDestination.Detail)
                    },
                    onBack = onBack,
                    onDismissNotice = onDismissNotice,
                )
                PluginDestination.Tutorial -> PluginTutorialScreen(
                    onBack = { show(PluginDestination.List) },
                    onInstallSample = {
                        onInstallSample()
                        show(PluginDestination.List)
                    },
                )
                PluginDestination.Detail -> {
                    val plugin = target.plugin
                    if (plugin == null) {
                        PluginsScreen(
                            snapshot = snapshot,
                            notice = notice,
                            onImport = onImport,
                            onOpenTutorial = { show(PluginDestination.Tutorial) },
                            onOpenPlugin = { id ->
                                selectedId = id
                                show(PluginDestination.Detail)
                            },
                            onBack = onBack,
                            onDismissNotice = onDismissNotice,
                        )
                    } else {
                        PluginDetailScreen(
                            plugin = plugin,
                            onBack = { show(PluginDestination.List) },
                            onEnable = { onEnable(plugin.id, it) },
                            onGrant = { capability, granted -> onGrant(plugin.id, capability, granted) },
                            onOpenPage = {
                                onOpenPage(plugin.id)
                                show(PluginDestination.Page)
                            },
                            onDelete = {
                                onDelete(plugin.id)
                                selectedId = null
                                show(PluginDestination.List)
                            },
                        )
                    }
                }
                PluginDestination.Page -> PluginPageScreen(
                    title = target.plugin?.name.orEmpty(),
                    page = target.plugin?.page,
                    onBack = { show(PluginDestination.Detail) },
                    onAction = { actionId -> target.plugin?.id?.let { onAction(it, actionId) } },
                )
            }
        }
    }
}
