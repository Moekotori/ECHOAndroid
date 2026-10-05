package app.echo.android.feature.connect

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

@Composable
internal fun SystemCastOutputEntry(onOpen: (() -> Unit)?, available: Boolean) {
    if (onOpen == null) return
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = onOpen, enabled = available, modifier = Modifier.fillMaxWidth()) {
            EchoIcon(Icons.Rounded.Cast, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.system_cast_output))
        }
        Text(stringResource(if (available) R.string.system_cast_output_hint else R.string.system_cast_unavailable),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
