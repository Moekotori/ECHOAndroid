package app.echo.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

@Composable
internal fun LibrarySongDisplayOptions(
    showFileNames: Boolean,
    showPlayNext: Boolean,
    onShowFileNamesChange: (Boolean) -> Unit,
    onShowPlayNextChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LibrarySongDisplaySwitch(R.string.library_file_names, R.string.library_file_names_note,
            showFileNames, onShowFileNamesChange)
        LibrarySongDisplaySwitch(R.string.library_inline_play_next, R.string.library_inline_play_next_note,
            showPlayNext, onShowPlayNextChange)
    }
}

@Composable
private fun LibrarySongDisplaySwitch(title: Int, note: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title))
            Text(stringResource(note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EchoSwitch(checked, onCheckedChange = null)
    }
}
