package app.echo.android.feature.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

@Composable
internal fun SignalHelpButton(title: String, detail: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        EchoIcon(Icons.Outlined.Info, stringResource(R.string.signal_about_control, title), Modifier.size(18.dp))
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) },
        text = { Text(detail, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.signal_close_help)) } })
}
