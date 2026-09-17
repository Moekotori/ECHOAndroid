package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoEqualizerPresets

/** Keeps presets within reach without pushing the actual adjustment controls offscreen. */
@Composable
internal fun SignalEqPresetPicker(selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp),
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
