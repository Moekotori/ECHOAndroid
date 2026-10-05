package app.echo.android.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoDropdownMenuItem

@Composable
internal fun LibraryDesktopShortcutButton(onPin: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.library_desktop_shortcut))
        }
        EchoDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            EchoDropdownMenuItem(selected = false, text = { Text(stringResource(R.string.library_desktop_open)) },
                onClick = { expanded = false; onPin(false) })
            EchoDropdownMenuItem(selected = false, text = { Text(stringResource(R.string.library_desktop_play)) },
                onClick = { expanded = false; onPin(true) })
        }
    }
}
