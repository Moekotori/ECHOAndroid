package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoDropdownMenuItem

internal data class UsbParameterChoice(val id: String, val label: String)

@Composable
internal fun UsbOutputParameterPicker(title: String, value: String, selected: String,
    choices: List<UsbParameterChoice>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().settingsSearchAnchor(title)) {
        Surface(onClick = { expanded = true }, enabled = enabled, color = androidx.compose.ui.graphics.Color.Transparent,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)) {
            Row(Modifier.heightIn(min = 60.dp).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(value, Modifier.widthIn(max = 170.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                EchoIcon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        EchoDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 220.dp, max = 320.dp)) {
            choices.forEach { choice ->
                EchoDropdownMenuItem(text = { Text(choice.label) }, onClick = { expanded = false; onSelect(choice.id) },
                    selected = choice.id == selected)
            }
        }
    }
}
