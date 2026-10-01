package app.echo.android.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

internal object LibrarySourceIds {
    const val Local = "local"
    const val PcEcho = "pc_echo"
    const val Cloud = "cloud"
}

internal enum class LibrarySourceMode(val id: String, val icon: ImageVector) {
    Local(LibrarySourceIds.Local, Icons.Rounded.LibraryMusic),
    PcEcho(LibrarySourceIds.PcEcho, Icons.Rounded.Devices),
    Cloud(LibrarySourceIds.Cloud, Icons.Rounded.CloudQueue),
}

@Composable
private fun LibrarySourceMode.label(): String = stringResource(
    when (this) {
        LibrarySourceMode.Local -> R.string.feature_library_local_9b5178
        LibrarySourceMode.PcEcho -> R.string.feature_library_pc_echo_e0a2d4
        LibrarySourceMode.Cloud -> R.string.feature_library_cloud_466e60
    },
)

@Composable
internal fun LibrarySourcePicker(
    selectedSource: LibrarySourceMode,
    onSelectSource: (LibrarySourceMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val sourceLabel = selectedSource.label()
    val libraryLabel = stringResource(R.string.feature_library_source_4aff16)
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.widthIn(max = 136.dp).heightIn(min = 48.dp).semantics {
                contentDescription = "$libraryLabel: $sourceLabel"
            },
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Text(sourceLabel, Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(4.dp))
            EchoIcon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 176.dp)) {
            LibrarySourceMode.entries.forEach { source ->
                val isSelected = source == selectedSource
                DropdownMenuItem(
                    text = {
                        Text(source.label(), fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    onClick = {
                        expanded = false
                        onSelectSource(source)
                    },
                    modifier = Modifier.semantics { selected = isSelected },
                    leadingIcon = { EchoIcon(source.icon, contentDescription = null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (isSelected) EchoIcon(Icons.Rounded.Check, contentDescription = null, Modifier.size(18.dp))
                    },
                )
            }
        }
    }
}
