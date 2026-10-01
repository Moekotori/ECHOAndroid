package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
                Box(
                    Modifier.weight(1f)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(mode) })
                        .drawBehind {
                            if (selected) {
                                val width = 24.dp.toPx()
                                val height = 2.dp.toPx()
                                drawRoundRect(scheme.primary,
                                    topLeft = Offset((size.width - width) / 2f, size.height - height),
                                    size = Size(width, height), cornerRadius = CornerRadius(height / 2f))
                            }
                        }
                        .heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(label), textAlign = TextAlign.Center,
                        color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
