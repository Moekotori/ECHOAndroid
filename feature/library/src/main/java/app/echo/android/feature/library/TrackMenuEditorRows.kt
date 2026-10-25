package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoSwitch
import app.echo.android.model.settings.*

@Composable
internal fun TrackMenuEditorRows(layout: EchoLibraryLayout, onChange: (EchoLibraryLayout) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text(stringResource(R.string.track_menu_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.track_menu_detail), style = MaterialTheme.typography.bodySmall)
        EchoTrackMenuAction.entries.forEach { action ->
            val label = stringResource(when(action) {
                EchoTrackMenuAction.EditTags -> R.string.feature_library_edit_tags_c8ec75
                EchoTrackMenuAction.ImportLyrics -> R.string.feature_library_import_lrc_lyrics_8e2810
                EchoTrackMenuAction.Artwork -> R.string.feature_library_custom_artwork_c8c999
                EchoTrackMenuAction.Versions -> R.string.track_versions_title
                EchoTrackMenuAction.Hide -> R.string.hidden_track_hide
                EchoTrackMenuAction.Delete -> R.string.feature_library_delete_138ccf
            })
            val visible = action !in layout.hiddenTrackActions
            Row(Modifier.fillMaxWidth().toggleable(visible, role = Role.Switch, onValueChange = { show ->
                onChange(layout.copy(hiddenTrackActions = if(show) layout.hiddenTrackActions - action else layout.hiddenTrackActions + action))
            }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f))
                EchoSwitch(visible, onCheckedChange = null)
            }
        }
    }
}
