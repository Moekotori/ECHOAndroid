package app.echo.android.feature.library

import app.echo.android.design.EchoSwitch
import app.echo.android.design.EchoIcon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.echo.android.model.settings.EchoLibraryLayout
import app.echo.android.model.settings.EchoLibraryCategory

private val LibraryLayoutDraftSaver = listSaver<EchoLibraryLayout, String>(
    save = { layout -> layout.normalized().order.map { section ->
        if (section in layout.hidden) "-${section.id}" else section.id
    } },
    restore = { saved -> EchoLibraryLayout(
        order = saved.mapNotNull { EchoLibraryCategory.fromId(it.removePrefix("-")) },
        hidden = saved.filter { it.startsWith("-") }
            .mapNotNull { EchoLibraryCategory.fromId(it.removePrefix("-")) }.toSet(),
    ).normalized() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryLayoutEditor(
    layout: EchoLibraryLayout,
    onDismiss: () -> Unit,
    onApply: (EchoLibraryLayout) -> Unit,
) {
    // The sheet owns a single editing session; cancel never modifies persisted settings.
    var draft by rememberSaveable(stateSaver = LibraryLayoutDraftSaver) { mutableStateOf(layout.normalized()) }
    val maxHeight = minOf(520.dp, (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Offset-dependent top padding would resize the sheet and move its expanded anchor.
        contentWindowInsets = {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
        },
    ) {
        // Keep the anchor stable while the lazy list scrolls or sections are reordered.
        Column(Modifier.fillMaxWidth().height(maxHeight).padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.library_layout_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.library_layout_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(draft.order, key = { _, section -> section.id }) { index, section ->
                    val label = section.viewMode().label()
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f).padding(end = 4.dp),
                            style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { draft = draft.move(section, -1) }, enabled = index > 0) {
                            EchoIcon(Icons.Rounded.KeyboardArrowUp,
                                stringResource(R.string.library_layout_move_up, label))
                        }
                        IconButton(onClick = { draft = draft.move(section, 1) }, enabled = index < draft.order.lastIndex) {
                            EchoIcon(Icons.Rounded.KeyboardArrowDown,
                                stringResource(R.string.library_layout_move_down, label))
                        }
                        EchoSwitch(checked = section !in draft.hidden,
                            enabled = section in draft.hidden || draft.visibleCategories().size > 1,
                            onCheckedChange = { draft = draft.withVisibility(section, it) },
                            modifier = Modifier.semantics { contentDescription = label })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { draft = EchoLibraryLayout() }) {
                    Text(stringResource(R.string.library_layout_reset))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_layout_cancel)) }
                TextButton(onClick = { onApply(draft); onDismiss() }) {
                    Text(stringResource(R.string.library_layout_apply))
                }
            }
        }
    }
}

@Composable
internal fun LibraryCustomizeButton(onCustomize: (() -> Unit)?) {
    if (onCustomize == null) return
    IconButton(onClick = onCustomize) {
        EchoIcon(Icons.AutoMirrored.Rounded.Sort,
            stringResource(R.string.library_layout_title))
    }
}
