package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.LibraryScanOptions

@Composable
internal fun ScanNameKeywords(options: LibraryScanOptions, enabled: Boolean, onChange: (LibraryScanOptions) -> Unit) {
    var text by remember { mutableStateOf(options.excludedNameKeywords.joinToString("\n")) }
    var emitted by remember { mutableStateOf(options.excludedNameKeywords) }
    LaunchedEffect(options.excludedNameKeywords) {
        if (options.excludedNameKeywords != emitted) { text = options.excludedNameKeywords.joinToString("\n"); emitted = options.excludedNameKeywords }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(text, { value ->
            text = value.take(4_096)
            emitted = text.lines().map { it.trim().take(64) }.filter(String::isNotBlank).take(50).toSet()
            onChange(options.copy(excludedNameKeywords = emitted))
        }, label = { Text(stringResource(R.string.scan_name_keywords)) }, enabled = enabled,
            minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.scan_name_keywords_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
