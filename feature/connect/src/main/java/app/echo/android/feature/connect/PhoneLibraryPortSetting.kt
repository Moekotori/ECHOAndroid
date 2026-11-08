package app.echo.android.feature.connect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.echo.android.model.connect.EchoLinkSharingPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PhoneLibraryPortSetting(configuredPort: Int, onChange: suspend (Int) -> Unit) {
    var value by rememberSaveable(configuredPort) { mutableStateOf(configuredPort.toString()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val port = value.toIntOrNull()
    val valid = port != null && EchoLinkSharingPort.isValid(port)
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(value, { if (it.length <= 5) value = it; failed = false }, singleLine = true,
            label = { Text(stringResource(R.string.phone_library_port_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = !valid, shape = ConnectControlShape)
        Text(stringResource(if (valid) R.string.phone_library_port_hint else R.string.phone_library_port_invalid),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(enabled = valid && !busy && port != configuredPort, onClick = {
            val selected = port ?: return@TextButton
            busy = true; failed = false
            scope.launch {
                try { onChange(selected) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.phone_library_port_save)) }
        if (failed) Text(stringResource(R.string.phone_library_port_failed),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
