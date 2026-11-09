package app.echo.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import java.util.Locale

internal val LosslessScanExtensions = setOf(
    "flac", "ape", "wv", "tta", "tak", "wav", "w64", "aiff", "aif", "aifc", "dsf", "dff",
    "alac",
)
private val OtherScanExtensions = listOf(
    "mp3", "mp2", "m4a", "m4b", "aac", "ogg", "oga", "opus", "mka",
    "ac3", "eac3", "dts", "amr", "wma", "asf",
)

@Composable
internal fun LibraryScanFormatOptions(
    selectedExtensions: Set<String>,
    enabled: Boolean = true,
    blocking: Boolean = false,
    onSelectionChange: (Set<String>) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!blocking) Text(stringResource(R.string.scan_rules_allowlist_title), style = MaterialTheme.typography.titleSmall)
        ScanFormatGroup(stringResource(R.string.scan_rules_format_group_lossless), LosslessScanExtensions,
            selectedExtensions, enabled, onSelectionChange)
        ScanFormatGroup(stringResource(R.string.scan_rules_format_group_other), OtherScanExtensions,
            selectedExtensions, enabled, onSelectionChange)
        if (selectedExtensions.isNotEmpty()) TextButton(enabled = enabled, onClick = { onSelectionChange(emptySet()) }) {
            Text(stringResource(if (blocking) R.string.scan_rules_clear_blocked else R.string.scan_all_formats))
        }
    }
}

@Composable
private fun ScanFormatGroup(
    title: String,
    extensions: Collection<String>,
    selectedExtensions: Set<String>,
    enabled: Boolean,
    onSelectionChange: (Set<String>) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            extensions.forEach { extension ->
                val selected = extension in selectedExtensions
                FilterChip(
                    enabled = enabled,
                    selected = selected,
                    onClick = {
                        onSelectionChange(
                            if (selected) selectedExtensions - extension
                            else selectedExtensions + extension,
                        )
                    },
                    label = { Text(extension.uppercase(Locale.ROOT), fontFamily = FontFamily.Monospace) },
                    leadingIcon = if (selected) {
                        { EchoIcon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }
    }
}
