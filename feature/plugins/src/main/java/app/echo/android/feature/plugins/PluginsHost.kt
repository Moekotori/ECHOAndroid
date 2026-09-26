package app.echo.android.feature.plugins

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import app.echo.android.plugin.PluginCapability
import app.echo.android.plugin.PluginInstallResult
import app.echo.android.plugin.PluginsSnapshot

private enum class PluginDestination {
    List,
    Detail,
    Tutorial,
    Page,
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
) {
    var destinationName by rememberSaveable { mutableStateOf(PluginDestination.List.name) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val destination = PluginDestination.valueOf(destinationName)
    fun show(next: PluginDestination) {
        destinationName = next.name
    }
    val selected = snapshot.plugins.firstOrNull { it.id == selectedId }
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
    BackHandler {
        when (destination) {
            PluginDestination.List -> onBack()
            PluginDestination.Page -> show(PluginDestination.Detail)
            else -> show(PluginDestination.List)
        }
    }
    when (destination) {
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
            val plugin = selected
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
            title = selected?.name.orEmpty(),
            page = selected?.page,
            onBack = { show(PluginDestination.Detail) },
            onAction = { actionId -> selectedId?.let { onAction(it, actionId) } },
        )
    }
}
