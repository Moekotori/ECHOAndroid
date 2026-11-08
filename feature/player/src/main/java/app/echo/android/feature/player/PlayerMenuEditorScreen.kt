package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoPageBackHandler
import app.echo.android.model.settings.EchoPlayerMenuAction
import app.echo.android.model.settings.EchoPlayerMenuLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val PlayerMenuDraftSaver = listSaver<EchoPlayerMenuLayout, String>(
    save = { layout -> layout.normalized().order.map { (if (it in layout.hidden) "!" else "") + it.id } },
    restore = { saved -> EchoPlayerMenuLayout(
        order = saved.mapNotNull { EchoPlayerMenuAction.fromId(it.removePrefix("!")) },
        hidden = saved.filter { it.startsWith("!") }.mapNotNull { EchoPlayerMenuAction.fromId(it.drop(1)) }.toSet(),
    ).normalized() },
)

@Composable
internal fun PlayerMenuEditorScreen(
    layout: EchoPlayerMenuLayout,
    onSave: suspend (EchoPlayerMenuLayout) -> Unit,
    onBack: () -> Unit,
) {
    var draft by rememberSaveable(stateSaver = PlayerMenuDraftSaver) { mutableStateOf(layout.normalized()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    EchoPageBackHandler { if (!busy) onBack() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !busy) {
                EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.player_appearance_back),
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.player_menu_edit), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(enabled = !busy, onClick = { draft = EchoPlayerMenuLayout(); failed = false }) {
                Text(stringResource(R.string.player_appearance_reset))
            }
            TextButton(enabled = !busy, onClick = {
                if (!busy) {
                    val saved = draft.normalized()
                    busy = true; failed = false
                    scope.launch {
                        try { onSave(saved); onBack() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true }
                        finally { busy = false }
                    }
                }
            }) { Text(stringResource(R.string.track_tools_save)) }
        }
        if (busy) LinearProgressIndicator(Modifier.widthIn(max = 720.dp).fillMaxWidth())
        if (failed) Text(stringResource(R.string.player_menu_save_failed), Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "hint") {
                Text(stringResource(R.string.player_menu_detail), Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(draft.order, key = { _, item -> item.id }) { index, item ->
                val label = stringResource(item.labelRes())
                val shown = item !in draft.hidden
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = shown, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = label },
                        onCheckedChange = { visible ->
                            draft = draft.copy(hidden = if (visible) draft.hidden - item else draft.hidden + item)
                        })
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        color = if (shown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(enabled = !busy && index > 0, onClick = { draft = draft.moved(item, -1) }) {
                        EchoIcon(Icons.Rounded.ArrowUpward, stringResource(R.string.player_menu_move_up, label),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (!busy && index > 0) 1f else 0.38f),
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(enabled = !busy && index < draft.order.lastIndex, onClick = { draft = draft.moved(item, 1) }) {
                        EchoIcon(Icons.Rounded.ArrowDownward, stringResource(R.string.player_menu_move_down, label),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (!busy && index < draft.order.lastIndex) 1f else 0.38f),
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
