package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoEqualizerPresets

/** Keeps presets within reach without pushing the actual adjustment controls offscreen. */
@Composable
internal fun SignalEqPresetPicker(selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(eqPresetLabel(selectedId), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EchoEqualizerPresets.presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(eqPresetLabel(preset.id)) },
                    trailingIcon = {
                        if (selectedId == preset.id) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    onClick = { expanded = false; onSelect(preset.id) },
                )
            }
        }
    }
}

