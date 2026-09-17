package app.echo.android.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource

@Composable
internal fun SettingsServicesContent(
    pcHandoffEnabled: Boolean,
    lastFmEnabled: Boolean,
    lastFmApiKey: String?,
    lastFmSharedSecret: String?,
    lastFmSessionKey: String?,
    lastFmStatusLabel: String,
    lastFmErrorLabel: String?,
    lastFmWebAuthPending: Boolean,
    lastFmApiKeyLocked: Boolean,
    lastFmSharedSecretLocked: Boolean,
    listenBrainzEnabled: Boolean,
    listenBrainzToken: String?,
    listenBrainzStatusLabel: String,
    listenBrainzErrorLabel: String?,
    setlistFmApiKey: String?,
    setlistFmApiKeyLocked: Boolean,
    onPcHandoffEnabledChange: (Boolean) -> Unit,
    onLastFmEnabledChange: (Boolean) -> Unit,
    onStartLastFmWebAuth: () -> Unit,
    onCompleteLastFmWebAuth: () -> Unit,
    onDisconnectLastFm: () -> Unit,
    onOpenLastFmApiAccounts: () -> Unit,
    onListenBrainzEnabledChange: (Boolean) -> Unit,
    onSaveListenBrainzToken: (String) -> Unit,
    onDisconnectListenBrainz: () -> Unit,
    onSaveSetlistFmApiKey: (String?) -> Unit,
    onOpenConnect: () -> Unit,
) {
    var lastFmApiKeyInput by rememberSaveable(lastFmApiKey) { mutableStateOf(lastFmApiKey.orEmpty()) }
    var lastFmSecretInput by rememberSaveable(lastFmSharedSecret) { mutableStateOf(lastFmSharedSecret.orEmpty()) }
    var listenBrainzTokenInput by rememberSaveable(listenBrainzToken) { mutableStateOf(listenBrainzToken.orEmpty()) }
    var setlistFmApiKeyInput by rememberSaveable(setlistFmApiKey) { mutableStateOf(setlistFmApiKey.orEmpty()) }
    SettingsSectionCard(
        title = stringResource(R.string.settings_section_connect),
    ) {
        LastFmSettingsPanel(
            enabled = lastFmEnabled,
            connected = !lastFmSessionKey.isNullOrBlank(),
            statusLabel = lastFmStatusLabel,
            errorLabel = lastFmErrorLabel,
            webAuthPending = lastFmWebAuthPending,
            apiKey = lastFmApiKeyInput,
            sharedSecret = lastFmSecretInput,
            apiKeyLocked = lastFmApiKeyLocked,
            sharedSecretLocked = lastFmSharedSecretLocked,
            onEnabledChange = onLastFmEnabledChange,
            onApiKeyChange = { lastFmApiKeyInput = it },
            onSharedSecretChange = { lastFmSecretInput = it },
            onStartWebAuth = onStartLastFmWebAuth,
            onCompleteWebAuth = onCompleteLastFmWebAuth,
            onDisconnect = onDisconnectLastFm,
            onOpenApiAccounts = onOpenLastFmApiAccounts,
        )
        ListenBrainzSettingsPanel(
            enabled = listenBrainzEnabled,
            connected = !listenBrainzToken.isNullOrBlank(),
            statusLabel = listenBrainzStatusLabel,
            errorLabel = listenBrainzErrorLabel,
            token = listenBrainzTokenInput,
            onEnabledChange = onListenBrainzEnabledChange,
            onTokenChange = { listenBrainzTokenInput = it },
            onSaveToken = { onSaveListenBrainzToken(listenBrainzTokenInput) },
            onDisconnect = onDisconnectListenBrainz,
        )
        SetlistFmSettingsPanel(
            apiKey = setlistFmApiKeyInput,
            locked = setlistFmApiKeyLocked,
            onApiKeyChange = { setlistFmApiKeyInput = it },
            onSave = { onSaveSetlistFmApiKey(setlistFmApiKeyInput) },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_pc_handoff),
            detail = stringResource(R.string.settings_pc_handoff_detail),
            checked = pcHandoffEnabled,
            onCheckedChange = onPcHandoffEnabledChange,
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_connect_pc),
            detail = if (pcHandoffEnabled) {
                stringResource(R.string.settings_connect_pc_detail)
            } else {
                stringResource(R.string.settings_connect_pc_disabled)
            },
            enabled = pcHandoffEnabled,
            onClick = onOpenConnect,
        )
    }
}
