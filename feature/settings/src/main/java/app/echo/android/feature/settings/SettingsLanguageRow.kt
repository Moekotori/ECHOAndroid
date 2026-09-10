package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsLanguageRow(selected: String, onSelect: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingsActionRow(
        title = stringResource(R.string.settings_language),
        detail = languageDetail(selected),
        onClick = { open = true },
    )
    if (open) {
        val options = languageOptions()
        val scroll = rememberLazyListState(initialFirstVisibleItemIndex = options.indexOfFirst { it.value == selected }.coerceAtLeast(0))
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp).selectableGroup(), state = scroll) {
                    items(options, key = { it.value }) { option ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = selected == option.value,
                                role = Role.RadioButton,
                                onClick = { open = false; onSelect(option.value) },
                            ).padding(vertical = 8.dp).heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = selected == option.value, onClick = null)
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.error_log_clear_cancel)) }
            },
        )
    }
}
