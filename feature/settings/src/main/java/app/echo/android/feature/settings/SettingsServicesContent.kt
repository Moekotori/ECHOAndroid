package app.echo.android.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource

@Composable
internal fun SettingsServicesContent(
    subNav: SettingsSubNav,
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
) {
    var lastFmApiKeyInput by rememberSaveable(lastFmApiKey) { mutableStateOf(lastFmApiKey.orEmpty()) }
    var lastFmSecretInput by rememberSaveable(lastFmSharedSecret) { mutableStateOf(lastFmSharedSecret.orEmpty()) }
    var listenBrainzTokenInput by rememberSaveable(listenBrainzToken) { mutableStateOf(listenBrainzToken.orEmpty()) }
    var setlistFmApiKeyInput by rememberSaveable(setlistFmApiKey) { mutableStateOf(setlistFmApiKey.orEmpty()) }
    val lastFmPanel: @Composable () -> Unit = {
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
    }
    val listenBrainzPanel: @Composable () -> Unit = {
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
    }
    val setlistFmPanel: @Composable () -> Unit = {
        SetlistFmSettingsPanel(
            apiKey = setlistFmApiKeyInput,
            locked = setlistFmApiKeyLocked,
            onApiKeyChange = { setlistFmApiKeyInput = it },
            onSave = { onSaveSetlistFmApiKey(setlistFmApiKeyInput) },
        )
    }
    val connectCard: @Composable () -> Unit = {
        SettingsSectionCard(title = stringResource(R.string.settings_section_connect)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_pc_handoff),
                detail = stringResource(R.string.settings_pc_handoff_detail),
                checked = pcHandoffEnabled,
                onCheckedChange = onPcHandoffEnabledChange,
            )
        }
    }
    when {
        // Two panes have room for everything at once.
        subNav.open == null -> {
            SettingsSectionCard(title = stringResource(R.string.settings_section_music_services)) {
                lastFmPanel()
                listenBrainzPanel()
                setlistFmPanel()
            }
            connectCard()
        }
        // The list shows each service's state; the switch for PC handoff needs no page of its own.
        subNav.isHub -> {
            SettingsSubPageRow(SettingsSubPage.ServicesLastFm, Icons.Rounded.Sync, subNav, summary = lastFmStatusLabel)
            SettingsSubPageRow(SettingsSubPage.ServicesListenBrainz, Icons.Rounded.Insights, subNav,
                summary = listenBrainzStatusLabel)
            SettingsSubPageRow(SettingsSubPage.ServicesSetlistFm, Icons.Rounded.Key, subNav)
            connectCard()
        }
        subNav.page == SettingsSubPage.ServicesLastFm ->
            SettingsSectionCard(title = stringResource(R.string.settings_search_lastfm)) { lastFmPanel() }
        subNav.page == SettingsSubPage.ServicesListenBrainz ->
            SettingsSectionCard(title = stringResource(R.string.settings_listenbrainz)) { listenBrainzPanel() }
        subNav.page == SettingsSubPage.ServicesSetlistFm ->
            SettingsSectionCard(title = stringResource(R.string.settings_setlistfm)) { setlistFmPanel() }
    }
}
