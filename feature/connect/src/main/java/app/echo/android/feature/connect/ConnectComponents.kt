package app.echo.android.feature.connect

import app.echo.android.feature.connect.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

internal val ConnectControlShape = RoundedCornerShape(4.dp)

@Composable
internal fun ConnectNote(text: String, error: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ConnectSection(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                if (!subtitle.isNullOrBlank()) ConnectNote(subtitle)
            }
            action?.invoke()
        }
        content()
    }
}

@Composable
internal fun ConnectInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    secret: Boolean = false,
    enabled: Boolean = true,
    url: Boolean = false,
    error: String? = null,
    onDone: (() -> Unit)? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, placeholder = { Text(placeholder) }, singleLine = true,
        shape = ConnectControlShape, enabled = enabled, isError = error != null,
        supportingText = error?.let { { ConnectNote(it, error = true) } },
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = when { secret -> KeyboardType.Password; url -> KeyboardType.Uri; else -> KeyboardType.Text },
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = if (secret) { {
            IconButton(onClick = { visible = !visible }, enabled = enabled) {
                Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) stringResource(L10nR.string.feature_connect_hide_password_3ea7f9) else stringResource(L10nR.string.feature_connect_show_password_211a6d))
            }
        } } else null,
    )
}

@Composable
internal fun ConnectPreference(title: String, detail: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            ConnectNote(detail)
        }
        Switch(checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
internal fun ForgetConnectionDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(8.dp),
        title = { Text(title) },
        text = { Text(stringResource(L10nR.string.feature_connect_saved_connection_details_will_be_removed_you_can_dc4b49)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(L10nR.string.feature_connect_remove_acf88e)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(L10nR.string.feature_connect_cancel_4c5fa5)) } },
    )
}
