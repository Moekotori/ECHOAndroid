package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun ThemeModeSelector(
    selectedMode: String,
    onSelect: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.settingsSearchAnchor(stringResource(R.string.settings_display_mode)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.settings_display_mode), color = scheme.onSurface,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "light" to R.string.settings_theme_light,
                "dark" to R.string.settings_theme_dark,
                "system" to R.string.settings_theme_system,
            ).forEach { (mode, label) ->
                val selected = selectedMode == mode
                Row(
                    Modifier.weight(1f)
                        .background(if (selected) scheme.primary.copy(alpha = 0.10f) else Color.Transparent)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(mode) })
                        .heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = scheme.primary)
                    Text(stringResource(label), textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                        color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
