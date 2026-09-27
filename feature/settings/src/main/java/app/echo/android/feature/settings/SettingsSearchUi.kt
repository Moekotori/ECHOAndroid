package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    val panelColor = settingsPanelColor()
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
        placeholder = {
            Text(stringResource(R.string.settings_search_hint), fontWeight = FontWeight.Normal)
        },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.settings_search_clear))
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = panelColor,
            unfocusedContainerColor = panelColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
internal fun SettingsSearchResults(
    results: List<SettingsSearchResult>,
    onSelect: (SettingsSearchResult) -> Unit,
) {
    if (results.isEmpty()) {
        Text(
            stringResource(R.string.settings_search_empty),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Surface(shape = RoundedCornerShape(18.dp), color = settingsPanelColor()) {
        Column {
            results.forEachIndexed { index, result ->
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                Surface(onClick = { onSelect(result) }, color = Color.Transparent) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 62.dp)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            result.item.category?.icon ?: Icons.Rounded.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                result.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                result.categoryTitle,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
