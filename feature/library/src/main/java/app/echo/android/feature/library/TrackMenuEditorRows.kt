package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoOrderDragState
import app.echo.android.design.EchoSheetOrderRow
import app.echo.android.model.settings.*

internal fun LazyListScope.trackMenuEditorRows(layout: EchoLibraryLayout, drag: EchoOrderDragState,
    onChange: (EchoLibraryLayout) -> Unit) {
    item(key = "track-menu-header") {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(stringResource(R.string.track_menu_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.track_menu_detail), style = MaterialTheme.typography.bodySmall)
        }
    }
    itemsIndexed(layout.trackActionOrder, key = { _, action -> "menu:${action.id}" }) { index, action ->
        val label = stringResource(when (action) {
            EchoTrackMenuAction.EditTags -> R.string.feature_library_edit_tags_c8ec75
            EchoTrackMenuAction.ImportLyrics -> R.string.feature_library_import_lrc_lyrics_8e2810
            EchoTrackMenuAction.Artwork -> R.string.feature_library_custom_artwork_c8c999
            EchoTrackMenuAction.Versions -> R.string.track_versions_title
            EchoTrackMenuAction.Hide -> R.string.hidden_track_hide
            EchoTrackMenuAction.Delete -> R.string.feature_library_delete_138ccf
        })
        EchoSheetOrderRow(
            position = index + 1, label = label, visible = action !in layout.hiddenTrackActions,
            dragState = drag, itemKey = "menu:${action.id}",
            canMoveUp = index > 0, canMoveDown = index < layout.trackActionOrder.lastIndex,
            moveUpDescription = stringResource(R.string.library_layout_move_up, label),
            moveDownDescription = stringResource(R.string.library_layout_move_down, label),
            onMoveUp = { onChange(layout.moveTrackAction(action, -1)) },
            onMoveDown = { onChange(layout.moveTrackAction(action, 1)) },
            onVisibleChange = { show ->
                onChange(layout.copy(hiddenTrackActions = if (show) layout.hiddenTrackActions - action else layout.hiddenTrackActions + action))
            },
        )
    }
}

private fun EchoLibraryLayout.moveTrackAction(action: EchoTrackMenuAction, delta: Int): EchoLibraryLayout {
    val order = trackActionOrder.toMutableList()
    val from = order.indexOf(action)
    val to = (from + delta).coerceIn(order.indices)
    order.add(to, order.removeAt(from))
    return copy(trackActionOrder = order)
}
