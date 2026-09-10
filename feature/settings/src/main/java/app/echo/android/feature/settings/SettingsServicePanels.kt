package app.echo.android.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.LocalEchoDarkTheme

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
    onStartWebAuth: () -> Unit,
    onCompleteWebAuth: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenApiAccounts: () -> Unit,
) {
    SettingsSwitchRow(
        title = "Last.fm Connect",
        detail = errorLabel ?: statusLabel,
        checked = enabled,
        onCheckedChange = onEnabledChange,
    )
    if (enabled) {
        if (apiKeyLocked) {
            SettingsActionRow(
                title = "API key",
                detail = stringResource(R.string.settings_lastfm_builtin),
                enabled = false,
                onClick = {},
            )
        } else {
            SettingsTextInputRow(
                title = "API key",
                value = apiKey,
                placeholder = "Last.fm API key",
                onValueChange = onApiKeyChange,
            )
        }
        if (sharedSecretLocked) {
            SettingsActionRow(
                title = "Shared secret",
                detail = stringResource(R.string.settings_lastfm_builtin),
                enabled = false,
                onClick = {},
            )
        } else {
            SettingsTextInputRow(
                title = "Shared secret",
                value = sharedSecret,
                placeholder = "Last.fm shared secret",
                secret = true,
                onValueChange = onSharedSecretChange,
            )
        }
        SettingsActionRow(
            title = if (connected) {
                stringResource(R.string.settings_lastfm_reauth)
            } else {
                stringResource(R.string.settings_lastfm_open_auth)
            },
            detail = stringResource(R.string.settings_lastfm_auth_detail),
            enabled = (apiKeyLocked || apiKey.isNotBlank()) && (sharedSecretLocked || sharedSecret.isNotBlank()),
            onClick = onStartWebAuth,
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_lastfm_complete_auth),
            detail = if (webAuthPending) {
                stringResource(R.string.settings_lastfm_complete_pending)
            } else {
                stringResource(R.string.settings_lastfm_complete_idle)
            },
            enabled = webAuthPending,
            onClick = onCompleteWebAuth,
        )
        SettingsActionRow(
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
    }
}

@Composable
internal fun ListenBrainzSettingsPanel(
    enabled: Boolean,
    connected: Boolean,
    statusLabel: String,
    errorLabel: String?,
    token: String,
    onEnabledChange: (Boolean) -> Unit,
    onTokenChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onDisconnect: () -> Unit,
) {
    SettingsSwitchRow(
        title = stringResource(R.string.settings_listenbrainz),
        detail = errorLabel ?: statusLabel,
        checked = enabled,
        onCheckedChange = onEnabledChange,
    )
    if (enabled) {
        SettingsTextInputRow(
            title = stringResource(R.string.settings_listenbrainz_token),
            value = token,
            placeholder = stringResource(R.string.settings_listenbrainz_token_placeholder),
            secret = true,
            onValueChange = onTokenChange,
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_listenbrainz_save),
            detail = stringResource(R.string.settings_listenbrainz_token_detail),
            enabled = token.isNotBlank(),
            onClick = onSaveToken,
        )
        if (connected) {
            SettingsActionRow(
                title = stringResource(R.string.settings_listenbrainz_disconnect),
                detail = stringResource(R.string.settings_listenbrainz_disconnect_detail),
                onClick = onDisconnect,
            )
        }
    }
}

@Composable
internal fun settingsPanelColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (LocalEchoDarkTheme.current) {
        EchoGlassPanel.copy(alpha = 0.58f)
    } else {
        scheme.surface.copy(alpha = 0.72f)
    }
}

@Composable
internal fun settingsRowColor(selected: Boolean = false): Color {
    val dark = LocalEchoDarkTheme.current
    return when {
        selected -> settingsControlColor().copy(alpha = if (dark) 0.22f else 0.14f)
        else -> Color.Transparent
    }
}

@Composable
internal fun settingsControlColor(): Color =
    MaterialTheme.colorScheme.primary

@Composable
internal fun settingsControlSurfaceColor(active: Boolean): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    return if (active) {
        settingsControlColor().copy(alpha = if (dark) 0.34f else 0.20f)
    } else if (dark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        scheme.outlineVariant.copy(alpha = 0.42f)
    }
}

