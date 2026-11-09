package app.echo.android.feature.library

import app.echo.android.design.EchoPageBackHandler as BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.LibraryScanOptions

/** Full-page editor. Draft changes stay local until the user saves them. */
@Composable
internal fun LibraryScanRulesScreen(
    initialOptions: LibraryScanOptions,
    enabled: Boolean,
    saving: Boolean,
    saveError: String?,
    selectedExcludedFolder: String?,
    onChooseExcludedFolder: () -> Unit,
    onExcludedFolderConsumed: () -> Unit,
    onBack: () -> Unit,
    onSave: (LibraryScanOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf(initialOptions) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var attemptedSave by rememberSaveable { mutableStateOf(false) }
    val dirty = draft != initialOptions
    val requestBack = {
        if (!saving) {
            if (dirty) confirmDiscard = true else onBack()
        }
    }
    LaunchedEffect(selectedExcludedFolder, enabled) {
        if (selectedExcludedFolder != null && enabled) {
            if (draft.excludedRelativePaths.size < MaxExcludedScanFolders &&
                draft.excludedRelativePaths.none { it.equals(selectedExcludedFolder, ignoreCase = true) }) {
                draft = draft.copy(excludedRelativePaths = draft.excludedRelativePaths + selectedExcludedFolder)
            }
            onExcludedFolderConsumed()
        }
    }
    BackHandler(onBack = requestBack)
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AddMusicPageHeader(
            stringResource(R.string.add_music_rules), stringResource(R.string.scan_rules_back), requestBack,
        ) {
            TextButton(enabled = enabled && draft != LibraryScanOptions(), onClick = { draft = LibraryScanOptions() }) {
                Text(stringResource(R.string.add_music_reset))
            }
        }
        Column(
            Modifier.weight(1f).widthIn(max = AddMusicContentWidth).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.scan_rules_applies_next),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LibraryScanFilters(draft, enabled, onChooseExcludedFolder) { draft = it }
        }
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier.widthIn(max = AddMusicContentWidth).fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (attemptedSave && saveError != null) Text(saveError,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(if (dirty) R.string.scan_rules_unsaved else R.string.scan_rules_up_to_date),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            enabled = enabled && dirty,
                            onClick = { attemptedSave = true; onSave(draft) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(if (saving) R.string.scan_rules_saving else R.string.add_music_save_rules)) }
                    }
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.metadata_discard_title)) },
            text = { Text(stringResource(R.string.scan_rules_discard_hint)) },
            confirmButton = {
                TextButton(onClick = onBack) { Text(stringResource(R.string.scan_rules_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.scan_rules_keep_editing)) }
            },
        )
    }
}
