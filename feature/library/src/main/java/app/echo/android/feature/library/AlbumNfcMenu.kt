package app.echo.android.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoDropdownMenuItem
import app.echo.android.design.EchoIcon

@Composable
internal fun AlbumNfcMenu(onWrite: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            EchoIcon(Icons.Rounded.MoreVert, stringResource(R.string.nfc_music_actions),
                tint = MaterialTheme.colorScheme.onSurface)
        }
        EchoDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EchoDropdownMenuItem(text = { Text(stringResource(R.string.nfc_music_write)) }, selected = false,
                onClick = { expanded = false; onWrite() })
        }
    }
}
