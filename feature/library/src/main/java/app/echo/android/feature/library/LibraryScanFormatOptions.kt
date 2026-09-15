package app.echo.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale

private val ScanExtensions = listOf(
    "flac", "wav", "aiff", "aif", "aifc", "dsf", "dff",
    "mp3", "mp2", "m4a", "m4b", "aac", "ogg", "oga", "opus", "mka",
    "ac3", "eac3", "dts", "amr",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryScanFormatOptions(
    selectedExtensions: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
) {
    Column {
        Text(stringResource(R.string.scan_audio_formats), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedExtensions.isEmpty(),
                onClick = { onSelectionChange(emptySet()) },
                label = { Text(stringResource(R.string.scan_all_formats)) },
            )
            ScanExtensions.forEach { extension ->
                FilterChip(
                    selected = extension in selectedExtensions,
                    onClick = {
                        onSelectionChange(
                            if (extension in selectedExtensions) selectedExtensions - extension
                            else selectedExtensions + extension,
                        )
                    },
                    label = { Text(extension.uppercase(Locale.ROOT)) },
                )
            }
        }
    }
}
