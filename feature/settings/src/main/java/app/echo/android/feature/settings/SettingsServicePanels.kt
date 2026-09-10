package app.echo.android.feature.settings

import app.echo.android.design.backgroundMaxBlur
import app.echo.android.design.echoAnimateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import app.echo.android.design.echoClickable
import app.echo.android.design.echoCombinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoHapticKind
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.LocalEchoHapticsEnabled
import app.echo.android.design.PageChrome
import app.echo.android.design.performEchoHaptic
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.settings.EchoBackgroundStyle
import app.echo.android.model.settings.EchoAppLanguage
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import app.echo.android.model.settings.EchoPerformanceMode
import kotlin.math.roundToInt

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

