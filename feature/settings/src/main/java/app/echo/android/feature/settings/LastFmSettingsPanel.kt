package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun LastFmSettingsPanel(
    enabled: Boolean,
    connected: Boolean,
    statusLabel: String,
    errorLabel: String?,
    webAuthPending: Boolean,
    apiKey: String,
    sharedSecret: String,
    apiKeyLocked: Boolean,
    sharedSecretLocked: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSharedSecretChange: (String) -> Unit,
    onStartWebAuth: (String, String) -> Unit,
    connecting: Boolean,
    details: @Composable () -> Unit,
    onCompleteWebAuth: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenApiAccounts: () -> Unit,
) {
    SettingsSwitchRow(
        title = stringResource(R.string.settings_search_lastfm),
        detail = errorLabel ?: statusLabel,
        checked = enabled,
        onCheckedChange = onEnabledChange,
    )
    if (!apiKeyLocked || !sharedSecretLocked) {
        if (!apiKeyLocked) {
            SettingsTextInputRow(
                title = stringResource(R.string.settings_search_api_key),
                value = apiKey,
                placeholder = stringResource(R.string.settings_lastfm_api_key_placeholder),
                onValueChange = onApiKeyChange,
            )
        }
        if (!sharedSecretLocked) {
            SettingsTextInputRow(
                title = stringResource(R.string.settings_search_shared_secret),
                value = sharedSecret,
                placeholder = stringResource(R.string.settings_lastfm_shared_secret_placeholder),
                secret = true,
                onValueChange = onSharedSecretChange,
            )
        }
    }
    SettingsActionRow(
        title = if (connecting) stringResource(R.string.lastfm_connecting) else if (connected) {
            stringResource(R.string.settings_lastfm_reauth)
        } else {
            stringResource(R.string.settings_lastfm_open_auth)
        },
        detail = stringResource(R.string.settings_lastfm_auth_detail),
        enabled = !connecting && (apiKeyLocked || apiKey.isNotBlank()) && (sharedSecretLocked || sharedSecret.isNotBlank()),
        onClick = { onStartWebAuth(apiKey, sharedSecret) },
    )
    if (webAuthPending) SettingsActionRow(
        title = stringResource(R.string.settings_lastfm_complete_auth),
        detail = stringResource(R.string.settings_lastfm_complete_pending),
        enabled = webAuthPending && !connecting,
        onClick = onCompleteWebAuth,
    )
    if (!apiKeyLocked || !sharedSecretLocked) SettingsActionRow(
        title = stringResource(R.string.settings_lastfm_api_accounts),
        detail = stringResource(R.string.settings_lastfm_api_accounts_detail),
        onClick = onOpenApiAccounts,
    )
    if (connected) {
        SettingsActionRow(
            title = stringResource(R.string.settings_lastfm_disconnect),
            detail = stringResource(R.string.settings_lastfm_disconnect_detail),
            onClick = onDisconnect,
        )
    }
    details()
}
