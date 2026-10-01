package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import app.echo.android.design.EchoIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    val panelColor = settingsPanelColor()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
        placeholder = {
            Text(stringResource(R.string.settings_search_hint), fontWeight = FontWeight.Normal)
        },
        leadingIcon = { EchoIcon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    EchoIcon(Icons.Rounded.Close, contentDescription = stringResource(R.string.settings_search_clear))
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            focusManager.clearFocus()
            keyboardController?.hide()
        }),
        shape = SettingsShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = panelColor,
            unfocusedContainerColor = panelColor,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
internal fun SettingsSearchEmpty() {
    Text(stringResource(R.string.settings_search_empty),
        modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsContentInset, vertical = 24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun SettingsSearchResultRow(
    result: SettingsSearchResult,
    showDivider: Boolean,
    onSelect: () -> Unit,
) {
    Surface(shape = SettingsShape, color = settingsPanelColor()) {
        Column {
            if (showDivider) HorizontalDivider(
                modifier = Modifier.padding(start = SettingsContentInset + 32.dp, end = SettingsContentInset),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Surface(onClick = onSelect, shape = SettingsShape, color = Color.Transparent) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .padding(horizontal = SettingsContentInset, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    EchoIcon(result.item.category?.icon ?: SettingsCategoryIcons.Plugins, null,
                        Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(result.title, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(result.categoryTitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
