package app.echo.android.feature.library

import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetActionBar
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.EchoSheetOrderRow
import app.echo.android.design.echoItemMotion
import app.echo.android.design.EchoIcon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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
    } + (if (layout.showFileNames) listOf("@fileNames") else emptyList()) +
        (if (layout.showPlayNext) listOf("@playNext") else emptyList()) +
        (if (layout.showAlbumArtists) listOf("@albumArtists") else emptyList()) + layout.hiddenTrackActions.map { "@menu:${it.id}" } },
    restore = { saved -> EchoLibraryLayout(
        showFileNames = "@fileNames" in saved,
        showPlayNext = "@playNext" in saved,
        showAlbumArtists = "@albumArtists" in saved,
        hiddenTrackActions = saved.filter { it.startsWith("@menu:") }.mapNotNull { app.echo.android.model.settings.EchoTrackMenuAction.fromId(it.removePrefix("@menu:")) }.toSet(),
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
    EchoModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Offset-dependent top padding would resize the sheet and move its expanded anchor.
        contentWindowInsets = {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
        },
    ) {
        // Keep the anchor stable while the lazy list scrolls or sections are reordered.
        Column(Modifier.fillMaxWidth().height(maxHeight)) {
            EchoSheetHeader(
                title = stringResource(R.string.library_layout_title),
                subtitle = stringResource(R.string.library_layout_detail),
            )
            LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item(key = "duplicate-versions") { LibraryDuplicatesSettingsEntry() }
                item(key = "file-names") {
                    Row(Modifier.fillMaxWidth().toggleable(value = draft.showFileNames, role = Role.Switch,
                        onValueChange = { draft = draft.copy(showFileNames = it) }).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.library_file_names))
                            Text(stringResource(R.string.library_file_names_note), style = MaterialTheme.typography.bodySmall)
                        }
                        app.echo.android.design.EchoSwitch(draft.showFileNames, onCheckedChange = null)
                    }
                }
                item(key = "play-next") {
                    Row(Modifier.fillMaxWidth().toggleable(value = draft.showPlayNext, role = Role.Switch,
                        onValueChange = { draft = draft.copy(showPlayNext = it) }).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.library_inline_play_next))
                            Text(stringResource(R.string.library_inline_play_next_note), style = MaterialTheme.typography.bodySmall)
                        }
                        app.echo.android.design.EchoSwitch(draft.showPlayNext, onCheckedChange = null)
                    }
                }
                item(key = "track-menu") { TrackMenuEditorRows(draft) { draft = it } }
                item(key = "album-artists") {
                    Row(Modifier.fillMaxWidth().toggleable(value = draft.showAlbumArtists, role = Role.Switch,
                        onValueChange = { draft = draft.copy(showAlbumArtists = it) }).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.library_show_album_artists))
                            Text(stringResource(R.string.library_show_album_artists_note), style = MaterialTheme.typography.bodySmall)
                        }
                        app.echo.android.design.EchoSwitch(draft.showAlbumArtists, onCheckedChange = null)
                    }
                }
                itemsIndexed(draft.order, key = { _, section -> section.id }) { index, section ->
                    val label = section.viewMode().label()
                    EchoSheetOrderRow(
                        position = index + 1,
                        label = label,
                        visible = section !in draft.hidden,
                        canMoveUp = index > 0,
                        canMoveDown = index < draft.order.lastIndex,
                        moveUpDescription = stringResource(R.string.library_layout_move_up, label),
                        moveDownDescription = stringResource(R.string.library_layout_move_down, label),
                        onMoveUp = { draft = draft.move(section, -1) },
                        onMoveDown = { draft = draft.move(section, 1) },
                        onVisibleChange = { draft = draft.withVisibility(section, it) },
                        switchEnabled = section in draft.hidden || draft.visibleCategories().size > 1,
                        modifier = echoItemMotion(),
                    )
                }
            }
            EchoSheetActionBar(
                applyLabel = stringResource(R.string.library_layout_apply),
                onApply = { onApply(draft); onDismiss() },
                cancelLabel = stringResource(R.string.library_layout_cancel),
                onCancel = onDismiss,
                resetLabel = stringResource(R.string.library_layout_reset),
                onReset = { draft = EchoLibraryLayout() },
            )
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
