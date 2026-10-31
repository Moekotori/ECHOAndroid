package app.echo.android.feature.library

import app.echo.android.design.EchoPageBackHandler as BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.library.LibraryScanOptions

/** Full-page editor. Draft changes stay local until the user saves them. */
@Composable
internal fun LibraryScanRulesScreen(
    initialOptions: LibraryScanOptions,
    enabled: Boolean,
    selectedExcludedFolder: String?,
    onChooseExcludedFolder: () -> Unit,
    onExcludedFolderConsumed: () -> Unit,
    onBack: () -> Unit,
    onSave: (LibraryScanOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf(initialOptions) }
    LaunchedEffect(selectedExcludedFolder, enabled) {
        if (selectedExcludedFolder != null && enabled) {
            if (draft.excludedRelativePaths.size < MaxExcludedScanFolders &&
                draft.excludedRelativePaths.none { it.equals(selectedExcludedFolder, ignoreCase = true) }) {
                draft = draft.copy(excludedRelativePaths = draft.excludedRelativePaths + selectedExcludedFolder)
            }
            onExcludedFolderConsumed()
        }
    }
    BackHandler(onBack = onBack)
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.scan_rules_back))
            }
            Text(
                stringResource(R.string.add_music_rules),
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
            )
            TextButton(enabled = enabled, onClick = { draft = LibraryScanOptions() }) {
                Text(stringResource(R.string.add_music_reset))
            }
        }
        Column(
            Modifier.weight(1f).widthIn(max = 680.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.scan_rules_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LibraryScanFilters(draft, enabled, onChooseExcludedFolder) { draft = it }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.scan_rules_save_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = enabled,
                onClick = { onSave(draft) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text(stringResource(R.string.add_music_save_rules)) }
        }
    }
}
