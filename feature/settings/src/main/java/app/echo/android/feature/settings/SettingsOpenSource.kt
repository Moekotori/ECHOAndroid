package app.echo.android.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsOpenSourceCard(openLink: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingsAboutPanel {
        SettingsActionRow(
            title = stringResource(R.string.settings_open_source),
            detail = stringResource(R.string.settings_open_source_summary),
            onClick = { open = true },
        )
    }
    if (open) OpenSourceDialog(openLink = openLink, onDismiss = { open = false })
}

@Composable
private fun OpenSourceDialog(openLink: (String) -> Unit, onDismiss: () -> Unit) {
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = (listOf(echoProjectLicense) + bundledOpenSourceNotices).find { it.name == selectedName }
    val listState = rememberLazyListState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(enabled = selected != null) { selectedName = null }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (selected == null) Arrangement.End else Arrangement.SpaceBetween) {
                    if (selected != null) TextButton(onClick = { selectedName = null }) {
                        Text(stringResource(R.string.settings_licenses_back))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_licenses_close)) }
                }
                Text(selected?.name ?: stringResource(R.string.settings_open_source),
                    style = MaterialTheme.typography.headlineSmall)
                if (selected == null) {
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Text(stringResource(R.string.settings_project_license_description),
                                style = MaterialTheme.typography.bodyMedium)
                            SettingsActionRow(echoProjectLicense.name, echoProjectLicense.license) {
                                selectedName = echoProjectLicense.name
                            }
                        }
                        item {
                            HorizontalDivider()
                            Text(stringResource(R.string.settings_bundled_notices),
                                Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.settings_bundled_notices_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(bundledOpenSourceNotices, key = { it.name }) { notice ->
                            SettingsActionRow(notice.name, notice.license) { selectedName = notice.name }
                        }
                    }
                } else {
                    Text(selected.license, style = MaterialTheme.typography.labelLarge)
                    selected.source?.let { source ->
                        TextButton(onClick = { openLink(source) }) {
                            Text(stringResource(R.string.settings_licenses_source))
                        }
                    }
                    key(selected.name) { LicenseText(selected) }
                }
            }
        }
    }
}

@Composable
private fun LicenseText(notice: OpenSourceNotice) {
    val assets = LocalContext.current.applicationContext.assets
    var retry by rememberSaveable(notice.name) { mutableIntStateOf(0) }
    val text by produceState<Result<List<String>>?>(null, notice, retry) {
        value = null
        value = withContext(Dispatchers.IO) {
            try {
                Result.success(notice.files.flatMap { path ->
                    assets.open("echo-licenses/$path").bufferedReader(Charsets.UTF_8).use { reader ->
                        // Short blocks keep layout bounded even for long GNU license texts.
                        reader.readText().replace("\r\n", "\n").split("\n\n")
                    }
                })
            } catch (error: IOException) {
                Result.failure(error)
            }
        }
    }
    val result = text
    when {
        result == null -> CircularProgressIndicator()
        result.isFailure -> {
            Text(stringResource(R.string.settings_licenses_read_error))
            TextButton(onClick = { retry++ }) { Text(stringResource(R.string.settings_licenses_retry)) }
        }
        else -> SelectionContainer {
            LazyColumn(state = rememberLazyListState(),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(result.getOrThrow()) { paragraph ->
                    Text(paragraph, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
