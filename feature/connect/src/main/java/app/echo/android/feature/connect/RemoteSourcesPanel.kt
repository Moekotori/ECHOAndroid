package app.echo.android.feature.connect

import app.echo.android.design.EchoIcon

import app.echo.android.feature.connect.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
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
    smbServerUrl: String?, smbUsername: String?, smbPassword: String?,
    onSyncSmb: (String, String, String) -> Unit,
    onSaveSmb: (String, String, String) -> Unit,
    onClearSmb: () -> Unit,
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(L10nR.string.feature_connect_your_music_wherever_it_lives_e9713c),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ConnectNote(stringResource(L10nR.string.feature_connect_add_a_music_server_or_a_nas_folder_7b39d3))
        }
        if (scanState.phase != LibraryScanPhase.Idle) {
            ConnectCard(highlighted = scanState.isScanning, spacing = 8.dp) {
                Text(remoteScanPhaseLabel(scanState.phase), style = MaterialTheme.typography.titleSmall)
                if (scanState.isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                ConnectNote(remoteLibraryDetail(scanState, ready = true), error = scanState.phase == LibraryScanPhase.Error)
                if (scanState.isScanning) TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(L10nR.string.feature_connect_cancel_sync_8888f1))
                }
            }
        }
        ConnectSourceLayout {
            SourceEditor(
                modifier = Modifier.weight(1f),
                title = "Subsonic / Navidrome", icon = Icons.Rounded.Dns,
                description = stringResource(L10nR.string.feature_connect_your_music_server_dd7eb8),
                placeholder = "https://music.example.com",
                savedUrl = subsonicServerUrl, savedUsername = subsonicUsername, savedPassword = subsonicPassword,
                expanded = expandedSource == "subsonic", onExpand = { expandedSource = if (expandedSource == "subsonic") null else "subsonic" },
                busy = scanState.isScanning, onSave = onSaveSubsonic, onSync = onSyncSubsonic, onClear = onClearSubsonic,
            )
            SourceEditor(
                modifier = Modifier.weight(1f),
                title = "WebDAV", icon = Icons.Rounded.Cloud,
                description = stringResource(L10nR.string.feature_connect_a_nas_or_cloud_music_folder_5525c7),
                placeholder = "https://dav.example.com/music",
                savedUrl = webDavServerUrl, savedUsername = webDavUsername, savedPassword = webDavPassword,
                expanded = expandedSource == "webdav", onExpand = { expandedSource = if (expandedSource == "webdav") null else "webdav" },
                busy = scanState.isScanning, onSave = onSaveWebDav, onSync = onSyncWebDav, onClear = onClearWebDav,
                allowAnonymous = true,
            )
            SourceEditor(
                modifier = Modifier.weight(1f),
                title = "SMB", icon = Icons.Rounded.Folder,
                description = stringResource(L10nR.string.connect_smb_description),
                placeholder = "smb://192.168.1.10/Music",
                savedUrl = smbServerUrl, savedUsername = smbUsername, savedPassword = smbPassword,
                expanded = expandedSource == "smb", onExpand = { expandedSource = if (expandedSource == "smb") null else "smb" },
                busy = scanState.isScanning, onSave = onSaveSmb, onSync = onSyncSmb, onClear = onClearSmb,
                isValidUrl = ::isSmbAddress,
                invalidUrlMessage = stringResource(L10nR.string.connect_smb_invalid_address),
                credentialsOptional = true,
                usernameHint = stringResource(L10nR.string.connect_smb_username_hint),
            )
            SourceEditor(
                modifier = Modifier.weight(1f),
                title = "Jellyfin / Emby", icon = Icons.Rounded.VideoLibrary,
                description = stringResource(L10nR.string.feature_connect_jellyfin_or_emby_independent_of_echo_link_a8f3c1),
                placeholder = "http://192.168.1.10:8096",
                savedUrl = jellyfinServerUrl, savedUsername = jellyfinUsername, savedPassword = jellyfinPassword,
                expanded = expandedSource == "jellyfin", onExpand = { expandedSource = if (expandedSource == "jellyfin") null else "jellyfin" },
                busy = scanState.isScanning, onSave = onSaveJellyfin, onSync = onSyncJellyfin, onClear = onClearJellyfin,
            )
        }
        ConnectSection(stringResource(L10nR.string.feature_connect_music_on_this_phone_8611a5)) {
            ConnectNote(stringResource(L10nR.string.feature_connect_local_files_are_managed_in_library_no_server_c5274c))
        }
    }
}

@Composable
private fun SourceEditor(
    modifier: Modifier = Modifier,
    title: String, icon: ImageVector, description: String, placeholder: String,
    savedUrl: String?, savedUsername: String?, savedPassword: String?,
    expanded: Boolean, onExpand: () -> Unit, busy: Boolean,
    onSave: (String, String, String) -> Unit,
    onSync: (String, String, String) -> Unit,
    onClear: () -> Unit,
    isValidUrl: (String) -> Boolean = ::isHttpAddress,
    invalidUrlMessage: String? = null,
    /** SMB 可以访客访问：用户名、密码都可以留空。 */
    credentialsOptional: Boolean = false,
    usernameHint: String? = null,
    allowAnonymous: Boolean = false,
) {
    var url by rememberSaveable(savedUrl) { mutableStateOf(savedUrl.orEmpty()) }
    var username by rememberSaveable(savedUsername) { mutableStateOf(savedUsername.orEmpty()) }
    var password by rememberSaveable(savedPassword) { mutableStateOf(savedPassword.orEmpty()) }
    var anonymous by rememberSaveable(savedUrl, savedUsername) { mutableStateOf(allowAnonymous && !savedUrl.isNullOrBlank() && savedUsername.isNullOrBlank()) }
    var confirmingRemoval by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val saved = !savedUrl.isNullOrBlank() &&
        (credentialsOptional || (allowAnonymous && savedUsername.isNullOrBlank()) || (!savedUsername.isNullOrBlank() && !savedPassword.isNullOrBlank()))
    val hasSaved = !savedUrl.isNullOrBlank() || !savedUsername.isNullOrBlank() || !savedPassword.isNullOrBlank()
    val effectiveUsername = if (anonymous) "" else username.trim()
    val effectivePassword = if (anonymous) "" else password
    val dirty = url != savedUrl.orEmpty() || effectiveUsername != savedUsername.orEmpty() || effectivePassword != savedPassword.orEmpty()
    val validUrl = isValidUrl(url.trim())
    val ready = validUrl && (
        if (anonymous) true else if (credentialsOptional) username.isBlank() || password.isNotBlank()
        else username.isNotBlank() && password.isNotBlank()
    )
    val submit = { if (ready && !busy) { keyboard?.hide(); onSync(url.trim(), effectiveUsername, effectivePassword) } }
    ConnectCard(modifier, contentPadding = ListCardPadding, spacing = 0.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).echoClickable(role = Role.Button) { focus.clearFocus(); keyboard?.hide(); onExpand() }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ConnectIconTile(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                ConnectNote(description)
                if (dirty) ConnectStatusChip(stringResource(L10nR.string.feature_connect_unsaved_changes_1ab4c9), ConnectTone.Warning)
                else if (saved) ConnectStatusChip(stringResource(L10nR.string.feature_connect_configuration_saved_a7628f), ConnectTone.Positive)
                else ConnectStatusChip(stringResource(L10nR.string.feature_connect_not_configured_29b98a), ConnectTone.Neutral)
            }
            EchoIcon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, Modifier.size(24.dp).echoExpandIndicator(expanded))
        }
        EchoExpand(expanded) {
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConnectInput(stringResource(L10nR.string.feature_connect_server_address_ec245e), url, { url = it }, placeholder,
                    enabled = !busy, url = true,
                    error = if (url.isNotBlank() && !validUrl) {
                        invalidUrlMessage ?: stringResource(L10nR.string.feature_connect_enter_a_full_http_or_https_address_3c7370)
                    } else null)
                if (allowAnonymous) ConnectPreference(stringResource(L10nR.string.connect_webdav_anonymous),
                    stringResource(L10nR.string.connect_webdav_anonymous_hint), anonymous, enabled = !busy,
                    onChange = { anonymous = it })
                if (!anonymous) {
                ConnectInput(stringResource(L10nR.string.feature_connect_username_dfb030), username, { username = it },
                    usernameHint ?: "", enabled = !busy)
                ConnectInput(stringResource(L10nR.string.feature_connect_password_2eaf7d), password, { password = it },
                    secret = true, enabled = !busy, onDone = submit)
                }
                Button(onClick = submit, enabled = ready && !busy, shape = ConnectControlShape, modifier = Modifier.fillMaxWidth()) {
                    Text(if (dirty || !saved) stringResource(L10nR.string.feature_connect_save_and_sync_957e87)
                        else stringResource(L10nR.string.feature_connect_sync_library_03a0c6))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { keyboard?.hide(); onSave(url.trim(), effectiveUsername, effectivePassword) }, enabled = ready && dirty && !busy) {
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

private fun isHttpAddress(value: String): Boolean =
    runCatching { URI(value).let { it.scheme?.lowercase() in listOf("http", "https") && !it.host.isNullOrBlank() } }
        .getOrDefault(false)

/** smb://host/share、\\host\share 或 host/share；至少要有主机和共享名。 */
private fun isSmbAddress(value: String): Boolean {
    var text = value.trim().replace('\\', '/')
    if (text.startsWith("smb://", ignoreCase = true)) text = text.substring(6)
    else if (text.contains("://")) return false
    val parts = text.trimStart('/').split('/').filter { it.isNotBlank() }
    return parts.size >= 2
}
