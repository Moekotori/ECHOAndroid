package app.echo.android.feature.connect

import app.echo.android.feature.connect.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.echoClickable
import app.echo.android.design.echoExpandIndicator
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress
import java.net.URI

@Composable
internal fun RemoteSourcesPanel(
    subsonicServerUrl: String?, subsonicUsername: String?, subsonicPassword: String?,
    webDavServerUrl: String?, webDavUsername: String?, webDavPassword: String?,
    jellyfinServerUrl: String?, jellyfinUsername: String?, jellyfinPassword: String?,
    scanState: LibraryScanProgress,
    onSyncSubsonic: (String, String, String) -> Unit,
    onSaveSubsonic: (String, String, String) -> Unit,
    onClearSubsonic: () -> Unit,
    onSyncWebDav: (String, String, String) -> Unit,
    onSaveWebDav: (String, String, String) -> Unit,
    onClearWebDav: () -> Unit,
    onSyncJellyfin: (String, String, String) -> Unit,
    onSaveJellyfin: (String, String, String) -> Unit,
    onClearJellyfin: () -> Unit,
    onCancel: () -> Unit,
) {
    var expandedSource by rememberSaveable { mutableStateOf<String?>(null) }
    // The backend exposes one shared scan state. Show it once, never attribute it to both providers.
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(L10nR.string.feature_connect_your_music_wherever_it_lives_e9713c),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            ConnectNote(stringResource(L10nR.string.feature_connect_add_a_music_server_or_a_nas_folder_7b39d3))
        }
        if (scanState.phase != LibraryScanPhase.Idle) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(remoteScanPhaseLabel(scanState.phase), style = MaterialTheme.typography.titleSmall)
                if (scanState.isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                ConnectNote(remoteLibraryDetail(scanState, ready = true), error = scanState.phase == LibraryScanPhase.Error)
                if (scanState.isScanning) TextButton(onClick = onCancel) {
                    Text(stringResource(L10nR.string.feature_connect_cancel_sync_8888f1))
                }
            }
        }
        SourceEditor(
            title = "Subsonic / Navidrome",
            description = stringResource(L10nR.string.feature_connect_your_music_server_dd7eb8),
            placeholder = "https://music.example.com",
            savedUrl = subsonicServerUrl, savedUsername = subsonicUsername, savedPassword = subsonicPassword,
            expanded = expandedSource == "subsonic", onExpand = { expandedSource = if (expandedSource == "subsonic") null else "subsonic" },
            busy = scanState.isScanning, onSave = onSaveSubsonic, onSync = onSyncSubsonic, onClear = onClearSubsonic,
        )
        SourceEditor(
            title = "WebDAV",
            description = stringResource(L10nR.string.feature_connect_a_nas_or_cloud_music_folder_5525c7),
            placeholder = "https://dav.example.com/music",
            savedUrl = webDavServerUrl, savedUsername = webDavUsername, savedPassword = webDavPassword,
            expanded = expandedSource == "webdav", onExpand = { expandedSource = if (expandedSource == "webdav") null else "webdav" },
            busy = scanState.isScanning, onSave = onSaveWebDav, onSync = onSyncWebDav, onClear = onClearWebDav,
        )
        SourceEditor(
            title = "Jellyfin / Emby",
            description = stringResource(L10nR.string.feature_connect_jellyfin_or_emby_independent_of_echo_link_a8f3c1),
            placeholder = "http://192.168.1.10:8096",
            savedUrl = jellyfinServerUrl, savedUsername = jellyfinUsername, savedPassword = jellyfinPassword,
            expanded = expandedSource == "jellyfin", onExpand = { expandedSource = if (expandedSource == "jellyfin") null else "jellyfin" },
            busy = scanState.isScanning, onSave = onSaveJellyfin, onSync = onSyncJellyfin, onClear = onClearJellyfin,
        )
        ConnectSection(stringResource(L10nR.string.feature_connect_music_on_this_phone_8611a5)) {
            ConnectNote(stringResource(L10nR.string.feature_connect_local_files_are_managed_in_library_no_server_c5274c))
        }
    }
}

@Composable
private fun SourceEditor(
    title: String, description: String, placeholder: String,
    savedUrl: String?, savedUsername: String?, savedPassword: String?,
    expanded: Boolean, onExpand: () -> Unit, busy: Boolean,
    onSave: (String, String, String) -> Unit,
    onSync: (String, String, String) -> Unit,
    onClear: () -> Unit,
) {
    var url by rememberSaveable(savedUrl) { mutableStateOf(savedUrl.orEmpty()) }
    var username by rememberSaveable(savedUsername) { mutableStateOf(savedUsername.orEmpty()) }
    var password by rememberSaveable(savedPassword) { mutableStateOf(savedPassword.orEmpty()) }
    var confirmingRemoval by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val saved = !savedUrl.isNullOrBlank() && !savedUsername.isNullOrBlank() && !savedPassword.isNullOrBlank()
    val hasSaved = !savedUrl.isNullOrBlank() || !savedUsername.isNullOrBlank() || !savedPassword.isNullOrBlank()
    val dirty = url != savedUrl.orEmpty() || username != savedUsername.orEmpty() || password != savedPassword.orEmpty()
    val validUrl = runCatching { URI(url.trim()).let { it.scheme?.lowercase() in listOf("http", "https") && !it.host.isNullOrBlank() } }.getOrDefault(false)
    val ready = validUrl && username.isNotBlank() && password.isNotBlank()
    val submit = { if (ready && !busy) { keyboard?.hide(); onSync(url.trim(), username.trim(), password) } }
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().echoClickable(role = Role.Button) { keyboard?.hide(); onExpand() }.padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                ConnectNote(description)
                ConnectNote(if (dirty) stringResource(L10nR.string.feature_connect_unsaved_changes_1ab4c9)
                    else if (saved) stringResource(L10nR.string.feature_connect_configuration_saved_a7628f)
                    else stringResource(L10nR.string.feature_connect_not_configured_29b98a))
            }
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, Modifier.size(24.dp).echoExpandIndicator(expanded))
        }
        EchoExpand(expanded) {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConnectInput(stringResource(L10nR.string.feature_connect_server_address_ec245e), url, { url = it }, placeholder,
                    enabled = !busy, url = true,
                    error = if (url.isNotBlank() && !validUrl) stringResource(L10nR.string.feature_connect_enter_a_full_http_or_https_address_3c7370) else null)
                ConnectInput(stringResource(L10nR.string.feature_connect_username_dfb030), username, { username = it }, enabled = !busy)
                ConnectInput(stringResource(L10nR.string.feature_connect_password_2eaf7d), password, { password = it },
                    secret = true, enabled = !busy, onDone = submit)
                Button(onClick = submit, enabled = ready && !busy, shape = ConnectControlShape, modifier = Modifier.fillMaxWidth()) {
                    Text(if (dirty || !saved) stringResource(L10nR.string.feature_connect_save_and_sync_957e87)
                        else stringResource(L10nR.string.feature_connect_sync_library_03a0c6))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { keyboard?.hide(); onSave(url.trim(), username.trim(), password) }, enabled = ready && dirty && !busy) {
                        Text(stringResource(L10nR.string.feature_connect_save_only_831a4d))
                    }
                    if (!hasSaved && dirty) TextButton(onClick = { url = ""; username = ""; password = "" }, enabled = !busy) {
                        Text(stringResource(L10nR.string.feature_connect_clear_ec6a55))
                    }
                    if (hasSaved) TextButton(onClick = { confirmingRemoval = true }, enabled = !busy) {
                        Text(stringResource(L10nR.string.feature_connect_remove_configuration_26bffc), color = MaterialTheme.colorScheme.error)
                    }
                }
                if (saved && !dirty) ConnectNote(stringResource(L10nR.string.feature_connect_saved_credentials_have_not_necessarily_been_verified_sync_b71597))
            }
        }
    }
    if (confirmingRemoval) ForgetConnectionDialog(
        title = stringResource(L10nR.string.feature_connect_remove_title_323067, (title).toString()),
        onDismiss = { confirmingRemoval = false },
        onConfirm = { confirmingRemoval = false; url = ""; username = ""; password = ""; onClear() },
    )
}
