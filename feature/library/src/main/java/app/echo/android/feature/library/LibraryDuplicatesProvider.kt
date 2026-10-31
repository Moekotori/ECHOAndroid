package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow

data class LibraryDuplicatesState(val enabled: Boolean = false, val groups: Int = 0, val hidden: Int = 0,
    val updatedAt: Long = 0, val skipped: Int = 0, val working: Boolean = false, val checked: Int = 0, val error: Boolean = false,
    val scanBlocked: Boolean = false, val cancelling: Boolean = false)

private val LocalDuplicatesSettings = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun LibraryDuplicatesProvider(state: StateFlow<LibraryDuplicatesState>, onAnalyze: () -> Unit, onCancel: () -> Unit,
    onEnabled: (Boolean) -> Unit, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val show = remember { { open = true } }
    CompositionLocalProvider(LocalDuplicatesSettings provides show, content = content)
    if (open) {
        val status by state.collectAsState()
        LibraryDuplicatesSheet(status, onAnalyze, onCancel, onEnabled, onDismiss = { open = false })
    }
}

@Composable
fun LibraryDuplicatesSettingsEntry() {
    val action = LocalDuplicatesSettings.current ?: return
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.duplicates_title))
            Text(stringResource(R.string.duplicates_entry_note), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = action) { Text(stringResource(R.string.duplicates_manage)) }
    }
}
