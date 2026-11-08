package app.echo.android.feature.connect

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun PhoneLibraryConnectionInfo(pcAddress: String?, listeningPort: Int?) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (pcAddress != null) SelectionContainer {
            Text(stringResource(R.string.link_connection_pc_address, pcAddress),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (listeningPort != null) {
            val port = listeningPort
            SelectionContainer {
                Text(stringResource(R.string.link_connection_frp_target, "127.0.0.1:$port"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ECHO Link",
                    "type = \"tcp\"\nlocalIP = \"127.0.0.1\"\nlocalPort = $port"))
            }) { Text(stringResource(R.string.link_connection_copy_frp)) }
        }
    }
}
