package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun UsbOutputModeControl(strict: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().settingsSearchAnchor(stringResource(R.string.settings_usb_bitperfect)),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(false, true).forEach { value ->
                    Surface(shape = RoundedCornerShape(12.dp),
                        color = if (strict == value) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.weight(1f)) {
                        Column(Modifier.heightIn(min = 48.dp).selectable(selected = strict == value,
                            role = Role.RadioButton, onClick = { onChange(value) }).padding(horizontal = 8.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(if (value) R.string.usb_mode_strict else R.string.usb_mode_processing),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (strict == value) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
        Text(stringResource(if (strict) R.string.usb_page_volume_note else R.string.usb_mode_processing_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
