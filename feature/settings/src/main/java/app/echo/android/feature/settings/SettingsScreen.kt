package app.echo.android.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import app.echo.android.design.EchoHapticKind
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.LocalEchoHapticsEnabled
import app.echo.android.design.PageChrome
import app.echo.android.design.performEchoHaptic
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.settings.EchoAppLanguage
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import app.echo.android.model.settings.EchoPerformanceMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    status: EchoPlaybackStatus,
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    appVersionLabel: String,
    dynamicArtworkEnabled: Boolean,
    compactModeEnabled: Boolean,
    dynamicColorEnabled: Boolean,
    playbackHapticsEnabled: Boolean,
    performanceMode: String,
    effectivePerformanceMode: String,
    trackAudioInfoTagsVisible: Boolean,
    pcHandoffEnabled: Boolean,
    discordPresenceViaPcEnabled: Boolean,
    showLyricsControlDeck: Boolean,
    onlineLyricsEnabled: Boolean,
    usbExclusiveEnabled: Boolean,
    usbExclusiveAutoRequestOnStartup: Boolean,
    usbExclusiveTestResult: String,
    customBackgroundMode: String,
    customBackgroundUri: String?,
    customBackgroundBlur: Float,
    customBackgroundBrightness: Float,
    customBackgroundGlass: Float,
    customBackgroundScale: Float,
    uiFontFamily: String,
    uiFontScale: Float,
    uiDensityScale: Float,
    lyricsFontFamily: String,
    lyricsFontScale: Float,
    importedFontUri: String?,
    themeMode: String,
    appLanguage: String,
    scheduledDarkModeEnabled: Boolean,
    scheduledDarkStartMinute: Int,
    scheduledDarkEndMinute: Int,
    lastFmEnabled: Boolean,
    lastFmApiKey: String?,
    lastFmSharedSecret: String?,
    lastFmSessionKey: String?,
    lastFmStatusLabel: String,
    lastFmErrorLabel: String?,
    lastFmWebAuthPending: Boolean,
    lastFmApiKeyLocked: Boolean,
    lastFmSharedSecretLocked: Boolean,
    onDynamicArtworkEnabledChange: (Boolean) -> Unit,
    onCompactModeEnabledChange: (Boolean) -> Unit,
    onDynamicColorEnabledChange: (Boolean) -> Unit,
    onPlaybackHapticsEnabledChange: (Boolean) -> Unit,
    onPerformanceModeChange: (String) -> Unit,
    onTrackAudioInfoTagsVisibleChange: (Boolean) -> Unit,
    onPcHandoffEnabledChange: (Boolean) -> Unit,
    onDiscordPresenceViaPcEnabledChange: (Boolean) -> Unit,
    onShowLyricsControlDeckChange: (Boolean) -> Unit,
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
    onUsbExclusiveEnabledChange: (Boolean) -> Unit,
    onUsbExclusiveAutoRequestOnStartupChange: (Boolean) -> Unit,
    onTestUsbExclusiveDriver: () -> Unit,
    onPickImageBackground: () -> Unit,
    onPickVideoBackground: () -> Unit,
    onClearCustomBackground: () -> Unit,
    onCustomBackgroundBlurChange: (Float) -> Unit,
    onCustomBackgroundBrightnessChange: (Float) -> Unit,
    onCustomBackgroundGlassChange: (Float) -> Unit,
    onCustomBackgroundScaleChange: (Float) -> Unit,
    onUiFontFamilyChange: (String) -> Unit,
    onUiFontScaleChange: (Float) -> Unit,
    onUiDensityScaleChange: (Float) -> Unit,
    onLyricsFontFamilyChange: (String) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onImportUiFont: () -> Unit,
    onImportLyricsFont: () -> Unit,
    onClearImportedFont: () -> Unit,
    onThemeModeChange: (String) -> Unit,
    onAppLanguageChange: (String) -> Unit,
    onScheduledDarkModeEnabledChange: (Boolean) -> Unit,
    onScheduledDarkStartMinuteChange: (Int) -> Unit,
    onScheduledDarkEndMinuteChange: (Int) -> Unit,
    onLastFmEnabledChange: (Boolean) -> Unit,
    onStartLastFmWebAuth: () -> Unit,
    onCompleteLastFmWebAuth: () -> Unit,
    onDisconnectLastFm: () -> Unit,
    onOpenLastFmApiAccounts: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenConnect: () -> Unit,
    notificationPermissionGranted: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
) {
    val sectionGap = if (compactModeEnabled) 6.dp else 10.dp
    val context = LocalContext.current
    val hapticsEnabled = LocalEchoHapticsEnabled.current
    var hiddenAppearanceUnlocked by rememberSaveable { mutableStateOf(false) }
    val showHiddenAppearanceOptions = hiddenAppearanceUnlocked
    val showThemeModeRow = showHiddenAppearanceOptions || themeMode != "dark"
    val showScheduledDarkMode = showHiddenAppearanceOptions || scheduledDarkModeEnabled
    var themeSectionExpanded by rememberSaveable { mutableStateOf(true) }
    var interfaceSectionExpanded by rememberSaveable { mutableStateOf(true) }
    var customBackgroundExpanded by rememberSaveable { mutableStateOf(true) }
    var customBackgroundAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
    var fontSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var playbackSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var connectSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var librarySectionExpanded by rememberSaveable { mutableStateOf(false) }
    var lastFmApiKeyInput by rememberSaveable(lastFmApiKey) { mutableStateOf(lastFmApiKey.orEmpty()) }
    var lastFmSecretInput by rememberSaveable(lastFmSharedSecret) { mutableStateOf(lastFmSharedSecret.orEmpty()) }

    PageChrome(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_subtitle),
        badge = stringResource(R.string.settings_badge),
        scrollable = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(sectionGap)) {
            SettingsHeroCard(
                status = status,
                trackCount = trackCount,
                albumCount = albumCount,
                artistCount = artistCount,
                dynamicArtwork = dynamicArtworkEnabled,
            )
            SettingsSectionCard(
                title = stringResource(R.string.settings_section_theme),
                collapsible = true,
                expanded = themeSectionExpanded,
                onExpandedChange = { themeSectionExpanded = it },
            ) {
                if (showThemeModeRow) {
                    SettingsChoiceGroupRow(
                        title = stringResource(R.string.settings_display_mode),
                        detail = themeDetail(themeMode),
                        options = themeOptions(includeHidden = showHiddenAppearanceOptions),
                        selectedValue = themeMode,
                        onOptionSelected = onThemeModeChange,
                    )
                }
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    detail = if (Build.VERSION.SDK_INT >= 31) {
                        stringResource(R.string.settings_dynamic_color_detail)
                    } else {
                        stringResource(R.string.settings_dynamic_color_unavailable)
                    },
                    checked = dynamicColorEnabled && Build.VERSION.SDK_INT >= 31,
                    onCheckedChange = onDynamicColorEnabledChange,
                    enabled = Build.VERSION.SDK_INT >= 31,
                )
                if (showScheduledDarkMode) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_scheduled_dark),
                        detail = stringResource(
                            R.string.settings_scheduled_dark_detail,
                            formatMinuteOfDay(scheduledDarkStartMinute),
                            formatMinuteOfDay(scheduledDarkEndMinute),
                        ),
                        checked = scheduledDarkModeEnabled,
                        onCheckedChange = onScheduledDarkModeEnabledChange,
                    )
                    if (scheduledDarkModeEnabled) {
                        SettingsSliderRow(
                            title = stringResource(R.string.settings_dark_start),
                            detail = formatMinuteOfDay(scheduledDarkStartMinute),
                            value = scheduledDarkStartMinute.toFloat(),
                            valueRange = 0f..1439f,
                            steps = 95,
                            onValueChange = { onScheduledDarkStartMinuteChange(it.roundToQuarterHour()) },
                        )
                        SettingsSliderRow(
                            title = stringResource(R.string.settings_dark_end),
                            detail = formatMinuteOfDay(scheduledDarkEndMinute),
                            value = scheduledDarkEndMinute.toFloat(),
                            valueRange = 0f..1439f,
                            steps = 95,
                            onValueChange = { onScheduledDarkEndMinuteChange(it.roundToQuarterHour()) },
                        )
                    }
                }
            }
            SettingsSectionCard(
                title = stringResource(R.string.settings_section_interface),
                collapsible = true,
                expanded = interfaceSectionExpanded,
                onExpandedChange = { interfaceSectionExpanded = it },
            ) {
                SettingsChoiceGroupRow(
                    title = stringResource(R.string.settings_language),
                    detail = languageDetail(appLanguage),
                    options = languageOptions(),
                    selectedValue = appLanguage,
                    onOptionSelected = onAppLanguageChange,
                )
                SettingsChoiceGroupRow(
                    title = stringResource(R.string.settings_performance_mode),
                    detail = performanceModeDetail(performanceMode, effectivePerformanceMode),
                    options = performanceModeOptions(),
                    selectedValue = performanceMode,
                    onOptionSelected = onPerformanceModeChange,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_artwork),
                    detail = stringResource(R.string.settings_dynamic_artwork_detail),
                    checked = dynamicArtworkEnabled,
                    onCheckedChange = onDynamicArtworkEnabledChange,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_compact_mode),
                    detail = stringResource(R.string.settings_compact_mode_detail),
                    checked = compactModeEnabled,
                    onCheckedChange = onCompactModeEnabledChange,
                )
            }
            SettingsSectionCard(
                title = stringResource(R.string.settings_section_background),
                collapsible = true,
                expanded = customBackgroundExpanded,
                onExpandedChange = { customBackgroundExpanded = it },
                persistentContent = {
                    SettingsBackgroundSourceRow(
                        mode = customBackgroundMode,
                        uri = customBackgroundUri,
                        onPickImageBackground = onPickImageBackground,
                        onPickVideoBackground = onPickVideoBackground,
                        onClearCustomBackground = onClearCustomBackground,
                    )
                },
            ) {
                SettingsDisclosureRow(
                    title = stringResource(R.string.settings_advanced),
                    detail = stringResource(R.string.settings_advanced_detail),
                    expanded = customBackgroundAdvancedExpanded,
                    onExpandedChange = { customBackgroundAdvancedExpanded = it },
                )
                if (customBackgroundAdvancedExpanded) {
                    SettingsSliderRow(
                        title = stringResource(R.string.settings_blur),
                        detail = "${customBackgroundBlur.roundToInt()} dp",
                        value = customBackgroundBlur,
                        valueRange = 0f..80f,
                        steps = 15,
                        onValueChange = onCustomBackgroundBlurChange,
                    )
                    SettingsSliderRow(
                        title = stringResource(R.string.settings_brightness),
                        detail = "${(customBackgroundBrightness * 100f).roundToInt()}%",
                        value = customBackgroundBrightness,
                        valueRange = 0.35f..1.15f,
                        steps = 15,
                        onValueChange = onCustomBackgroundBrightnessChange,
                    )
                    SettingsSliderRow(
                        title = stringResource(R.string.settings_glass),
                        detail = "${(customBackgroundGlass * 100f).roundToInt()}%",
                        value = customBackgroundGlass,
                        valueRange = 0.08f..0.90f,
                        steps = 13,
                        onValueChange = onCustomBackgroundGlassChange,
                    )
                    SettingsSliderRow(
                        title = stringResource(R.string.settings_scale),
                        detail = "${(customBackgroundScale * 100f).roundToInt()}%",
                        value = customBackgroundScale,
                        valueRange = 1.00f..1.40f,
                        steps = 15,
                        onValueChange = onCustomBackgroundScaleChange,
                    )
                }
            }
            SettingsSectionCard(
                title = stringResource(R.string.settings_section_fonts),
                collapsible = true,
                expanded = fontSectionExpanded,
                onExpandedChange = { fontSectionExpanded = it },
            ) {
                SettingsChoiceGroupRow(
                    title = stringResource(R.string.settings_ui_font),
                    detail = fontDetail(uiFontFamily, importedFontUri),
                    options = fontOptions(importedFontUri),
                    selectedValue = uiFontFamily,
                    onOptionSelected = { value ->
                        if (value == "imported" && importedFontUri.isNullOrBlank()) {
                            onImportUiFont()
                        } else {
                            onUiFontFamilyChange(value)
                        }
                    },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.settings_ui_font_size),
                    detail = "${(uiFontScale * 100f).roundToInt()}%",
                    value = uiFontScale,
                    valueRange = 0.88f..1.18f,
                    steps = 14,
                    onValueChange = onUiFontScaleChange,
                )
                SettingsSliderRow(
                    title = stringResource(R.string.settings_ui_density),
                    detail = "${(uiDensityScale * 100f).roundToInt()}%",
                    value = uiDensityScale,
                    valueRange = 0.90f..1.12f,
                    steps = 10,
                    onValueChange = onUiDensityScaleChange,
                )
                SettingsChoiceGroupRow(
                    title = stringResource(R.string.settings_lyrics_font),
                    detail = fontDetail(lyricsFontFamily, importedFontUri),
                    options = fontOptions(importedFontUri),
                    selectedValue = lyricsFontFamily,
                    onOptionSelected = { value ->
                        if (value == "imported" && importedFontUri.isNullOrBlank()) {
                            onImportLyricsFont()
                        } else {
                            onLyricsFontFamilyChange(value)
                        }
                    },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.settings_lyrics_font_size),
                    detail = "${(lyricsFontScale * 100f).roundToInt()}%",
                    value = lyricsFontScale,
                    valueRange = 0.82f..1.28f,
                    steps = 22,
                    onValueChange = onLyricsFontScaleChange,
                )
                SettingsActionRow(
                    title = stringResource(R.string.settings_reselect_font),
                    detail = if (importedFontUri.isNullOrBlank()) {
                        stringResource(R.string.settings_import_font_detail)
                    } else {
                        stringResource(
                            R.string.settings_import_font_current,
                            importedFontUri.substringAfterLast('/').takeLast(28),
                        )
                    },
                    onClick = onImportUiFont,
                )
                SettingsActionRow(
                    title = stringResource(R.string.settings_clear_font),
                    detail = if (importedFontUri.isNullOrBlank()) {
                        stringResource(R.string.settings_clear_font_empty)
                    } else {
                        stringResource(R.string.settings_clear_font_detail)
                    },
                    enabled = !importedFontUri.isNullOrBlank(),
                    onClick = onClearImportedFont,
                )
            }
            SettingsSectionCard(
                title = stringResource(R.string.settings_section_playback),
                collapsible = true,
                expanded = playbackSectionExpanded,
                onExpandedChange = { playbackSectionExpanded = it },
            ) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_lyrics_sync_tools),
                    detail = stringResource(R.string.settings_lyrics_sync_tools_detail),
                    checked = showLyricsControlDeck,
                    onCheckedChange = onShowLyricsControlDeckChange,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_playback_haptics),
                    detail = stringResource(R.string.settings_playback_haptics_detail),
                    checked = playbackHapticsEnabled,
                    onCheckedChange = onPlaybackHapticsEnabledChange,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_online_lyrics),
                    detail = stringResource(R.string.settings_online_lyrics_detail),
                    checked = onlineLyricsEnabled,
                    onCheckedChange = onOnlineLyricsEnabledChange,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_usb_exclusive),
                    detail = usbExclusiveDetail(status),
                    checked = usbExclusiveEnabled,
                    onCheckedChange = onUsbExclusiveEnabledChange,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_usb_auto_request),
                    detail = if (usbExclusiveAutoRequestOnStartup) {
                        stringResource(R.string.settings_usb_auto_request_on)
                    } else {
                        stringResource(R.string.settings_usb_auto_request_off)
                    },
                    checked = usbExclusiveAutoRequestOnStartup,
                    onCheckedChange = onUsbExclusiveAutoRequestOnStartupChange,
                )
                SettingsActionRow(
                    title = stringResource(R.string.settings_notification_permission),
                    detail = if (notificationPermissionGranted) {
                        stringResource(R.string.settings_notification_granted)
                    } else {
                        stringResource(R.string.settings_notification_denied)
                    },
                    actionLabel = if (notificationPermissionGranted) {
                        stringResource(R.string.settings_on)
                    } else {
                        stringResource(R.string.settings_allow)
                    },
                    enabled = !notificationPermissionGranted,
                    onClick = onRequestNotificationPermission,
                )
                SettingsActionRow(
                    title = stringResource(R.string.settings_test_usb),
                    detail = usbExclusiveTestDetail(status, usbExclusiveTestResult),
                    enabled = status.diagnostics.usbConnected,
                    actionLabel = stringResource(R.string.settings_test),
                    onClick = onTestUsbExclusiveDriver,
                )
            }
            SettingsSectionCard(
                title = stringResource(R.string.settings_section_connect),
                collapsible = true,
                expanded = connectSectionExpanded,
                onExpandedChange = { connectSectionExpanded = it },
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
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_pc_handoff),
                    detail = stringResource(R.string.settings_pc_handoff_detail),
                    checked = pcHandoffEnabled,
                    onCheckedChange = onPcHandoffEnabledChange,
                )
                SettingsSwitchRow(
                    title = "Discord Rich Presence",
                    detail = if (pcHandoffEnabled) {
                        stringResource(R.string.settings_discord_detail_on)
                    } else {
                        stringResource(R.string.settings_discord_detail_off)
                    },
                    checked = discordPresenceViaPcEnabled,
                    onCheckedChange = onDiscordPresenceViaPcEnabledChange,
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
            SettingsSectionCard(
                title = stringResource(R.string.settings_section_library),
                collapsible = true,
                expanded = librarySectionExpanded,
                onExpandedChange = { librarySectionExpanded = it },
            ) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_audio_tags),
                    detail = stringResource(R.string.settings_audio_tags_detail),
                    checked = trackAudioInfoTagsVisible,
                    onCheckedChange = onTrackAudioInfoTagsVisibleChange,
                )
                SettingsActionRow(
                    title = stringResource(R.string.settings_local_music),
                    detail = stringResource(R.string.settings_local_music_detail),
                    onClick = onOpenLibrary,
                )
            }
            SettingsSectionCard(title = stringResource(R.string.settings_section_about)) {
                SettingsInfoRow(
                    title = stringResource(R.string.settings_version),
                    detail = appVersionLabel,
                    onLongClick = {
                        if (!hiddenAppearanceUnlocked) {
                            hiddenAppearanceUnlocked = true
                            if (hapticsEnabled) {
                                context.performEchoHaptic(EchoHapticKind.Confirm)
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(156.dp))
        }
    }
}

@Composable
private fun LastFmSettingsPanel(
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
private fun settingsPanelColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (LocalEchoDarkTheme.current) {
        Color(0xFF2C2E35).copy(alpha = 0.58f)
    } else {
        scheme.surface.copy(alpha = 0.72f)
    }
}

@Composable
private fun settingsRowColor(selected: Boolean = false): Color {
    val dark = LocalEchoDarkTheme.current
    return when {
        selected -> settingsControlColor().copy(alpha = if (dark) 0.22f else 0.14f)
        else -> Color.Transparent
    }
}

@Composable
private fun settingsControlColor(): Color =
    if (LocalEchoDarkTheme.current) Color(0xFFD3A9B5) else MaterialTheme.colorScheme.primary

@Composable
private fun settingsControlSurfaceColor(active: Boolean): Color {
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

@Composable
private fun SettingsTextInputRow(
    title: String,
    value: String,
    placeholder: String,
    secret: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun backgroundDetail(mode: String, uri: String?): String {
    val fileName = uri?.substringAfterLast('/')?.takeLast(28)
    return when {
        mode == "image" && !fileName.isNullOrBlank() -> stringResource(R.string.settings_bg_image, fileName)
        mode == "video" && !fileName.isNullOrBlank() -> stringResource(R.string.settings_bg_video, fileName)
        else -> stringResource(R.string.settings_bg_default)
    }
}

private data class SettingsChoiceOption(
    val value: String,
    val label: String,
)

@Composable
private fun themeOptions(includeHidden: Boolean): List<SettingsChoiceOption> {
    val dark = SettingsChoiceOption("dark", stringResource(R.string.settings_theme_dark))
    if (!includeHidden) return listOf(dark)
    return listOf(
        SettingsChoiceOption("system", stringResource(R.string.settings_theme_system)),
        SettingsChoiceOption("light", stringResource(R.string.settings_theme_light)),
        dark,
    )
}

@Composable
private fun themeDetail(mode: String): String =
    when (mode) {
        "light" -> stringResource(R.string.settings_theme_detail_light)
        "dark" -> stringResource(R.string.settings_theme_detail_dark)
        else -> stringResource(R.string.settings_theme_detail_system)
    }

@Composable
private fun languageOptions(): List<SettingsChoiceOption> = listOf(
    SettingsChoiceOption(EchoAppLanguage.System, stringResource(R.string.settings_language_system)),
    SettingsChoiceOption(EchoAppLanguage.Chinese, stringResource(R.string.settings_language_zh)),
    SettingsChoiceOption(EchoAppLanguage.English, stringResource(R.string.settings_language_en)),
    SettingsChoiceOption(EchoAppLanguage.Japanese, stringResource(R.string.settings_language_ja)),
)

@Composable
private fun languageDetail(mode: String): String =
    when (EchoAppLanguage.fromId(mode)) {
        EchoAppLanguage.Chinese -> stringResource(R.string.settings_language_detail_zh)
        EchoAppLanguage.English -> stringResource(R.string.settings_language_detail_en)
        EchoAppLanguage.Japanese -> stringResource(R.string.settings_language_detail_ja)
        else -> stringResource(R.string.settings_language_detail_system)
    }

@Composable
private fun performanceModeOptions(): List<SettingsChoiceOption> = listOf(
    SettingsChoiceOption(EchoPerformanceMode.Auto.id, stringResource(R.string.settings_perf_auto)),
    SettingsChoiceOption(EchoPerformanceMode.Balanced.id, stringResource(R.string.settings_perf_balanced)),
    SettingsChoiceOption(EchoPerformanceMode.Lightweight.id, stringResource(R.string.settings_perf_lightweight)),
    SettingsChoiceOption(EchoPerformanceMode.HighPerformance.id, stringResource(R.string.settings_perf_high)),
)

@Composable
private fun performanceModeDetail(mode: String, effectiveMode: String): String {
    val effectiveLabel = when (EchoEffectivePerformanceMode.entries.firstOrNull { it.id == effectiveMode }) {
        EchoEffectivePerformanceMode.Lightweight -> stringResource(R.string.settings_perf_effective_light)
        EchoEffectivePerformanceMode.HighPerformance -> stringResource(R.string.settings_perf_effective_high)
        else -> stringResource(R.string.settings_perf_effective_balanced)
    }
    return when (EchoPerformanceMode.fromId(mode)) {
        EchoPerformanceMode.Auto -> stringResource(R.string.settings_perf_detail_auto, effectiveLabel)
        EchoPerformanceMode.Balanced -> stringResource(R.string.settings_perf_detail_balanced)
        EchoPerformanceMode.Lightweight -> stringResource(R.string.settings_perf_detail_lightweight)
        EchoPerformanceMode.HighPerformance -> stringResource(R.string.settings_perf_detail_high)
    }
}

@Composable
private fun fontOptions(importedFontUri: String?): List<SettingsChoiceOption> = buildList {
    add(SettingsChoiceOption("system", stringResource(R.string.settings_font_system)))
    add(SettingsChoiceOption("serif", stringResource(R.string.settings_font_serif)))
    add(SettingsChoiceOption("monospace", stringResource(R.string.settings_font_mono)))
    add(
        SettingsChoiceOption(
            "imported",
            if (importedFontUri.isNullOrBlank()) {
                stringResource(R.string.settings_font_import)
            } else {
                stringResource(R.string.settings_font_imported)
            },
        ),
    )
}

@Composable
private fun fontDetail(mode: String, importedFontUri: String?): String =
    when (mode) {
        "outfit" -> stringResource(R.string.settings_font_detail_system)
        "serif" -> stringResource(R.string.settings_font_detail_serif)
        "monospace" -> stringResource(R.string.settings_font_detail_mono)
        "imported" -> importedFontUri?.substringAfterLast('/')?.takeLast(28)?.let {
            stringResource(R.string.settings_font_detail_imported, it)
        } ?: stringResource(R.string.settings_font_detail_pick)
        else -> stringResource(R.string.settings_font_detail_system)
    }

private fun formatMinuteOfDay(value: Int): String {
    val minuteOfDay = value.coerceIn(0, 23 * 60 + 59)
    val hour = minuteOfDay / 60
    val minute = minuteOfDay % 60
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

private fun Float.roundToQuarterHour(): Int =
    ((this / 15f).roundToInt() * 15).coerceIn(0, 23 * 60 + 59)

@Composable
private fun SettingsHeroCard(
    status: EchoPlaybackStatus,
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    dynamicArtwork: Boolean,
) {
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(settingsPanelColor())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "ECHO Mobile",
                        color = if (dark) Color.White.copy(alpha = 0.96f) else scheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        status.track?.title ?: stringResource(R.string.settings_ready),
                        color = if (dark) Color.White.copy(alpha = 0.74f) else scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsStatTile(stringResource(R.string.settings_tracks), trackCount.toString(), Modifier.weight(1f))
                SettingsStatTile(stringResource(R.string.settings_albums), albumCount.toString(), Modifier.weight(1f))
                SettingsStatTile(stringResource(R.string.settings_artists), artistCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    collapsible: Boolean = false,
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
    persistentContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val animateSize = !LocalEchoEffectivePerformanceMode.current.isLightweight
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(settingsPanelColor())
            .then(if (animateSize) Modifier.animateContentSize() else Modifier)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = if (collapsible) {
                Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(vertical = 2.dp)
            } else {
                Modifier.fillMaxWidth()
            },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = if (dark) Color.White.copy(alpha = 0.96f) else scheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (collapsible) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (dark) Color.White.copy(alpha = 0.72f) else scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        persistentContent()
        if (expanded) {
            content()
        }
    }
}

@Composable
private fun SettingsDisclosureRow(
    title: String,
    detail: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    SettingsRowShell(
        title = title,
        detail = detail,
        modifier = Modifier.clickable { onExpandedChange(!expanded) },
    ) {
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = if (dark) Color.White.copy(alpha = 0.62f) else scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SettingsBackgroundSourceRow(
    mode: String,
    uri: String?,
    onPickImageBackground: () -> Unit,
    onPickVideoBackground: () -> Unit,
    onClearCustomBackground: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.settings_bg_source),
                color = if (dark) Color.White.copy(alpha = 0.94f) else scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                backgroundDetail(mode, uri),
                color = if (dark) Color.White.copy(alpha = 0.70f) else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BackgroundSourceAction(
                    label = stringResource(R.string.settings_bg_image_label),
                    selected = mode == "image" && !uri.isNullOrBlank(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onPickImageBackground,
                )
                BackgroundSourceAction(
                    label = stringResource(R.string.settings_bg_video_label),
                    selected = mode == "video" && !uri.isNullOrBlank(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onPickVideoBackground,
                )
                BackgroundSourceAction(
                    label = stringResource(R.string.settings_bg_default_label),
                    selected = uri.isNullOrBlank(),
                    enabled = !uri.isNullOrBlank(),
                    modifier = Modifier.weight(1f),
                    onClick = onClearCustomBackground,
                )
            }
        }
    }
}

@Composable
private fun BackgroundSourceAction(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val accent = if (selected) settingsControlColor() else if (dark) Color.White.copy(alpha = 0.74f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(settingsRowColor(selected))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled || selected) 1f else 0.48f)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SettingsRowShell(title = title, detail = detail) {
        EchoSettingsSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsInfoRow(
    title: String,
    detail: String,
    onLongClick: (() -> Unit)? = null,
) {
    SettingsRowShell(
        title = title,
        detail = detail,
        modifier = if (onLongClick != null) {
            Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
        } else {
            Modifier
        },
        trailing = {},
    )
}

@Composable
private fun EchoSettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(RoundedCornerShape(18.dp))
            .alpha(if (enabled) 1f else 0.42f)
            .background(settingsControlSurfaceColor(checked))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 5.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (dark) Color.White.copy(alpha = 0.72f) else scheme.onSurfaceVariant.copy(alpha = 0.82f)),
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    detail: String,
    enabled: Boolean = true,
    actionLabel: String? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val controlColor = settingsControlColor()
    val resolvedActionLabel = actionLabel ?: stringResource(R.string.settings_enter)
    SettingsRowShell(
        title = title,
        detail = detail,
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Text(
            if (enabled) resolvedActionLabel else stringResource(R.string.settings_closed),
            color = if (enabled) controlColor else if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.58f) else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingsChoiceGroupRow(
    title: String,
    detail: String,
    options: List<SettingsChoiceOption>,
    selectedValue: String,
    onOptionSelected: (String) -> Unit,
) {
    SettingsRowShell(title = title, detail = detail, trailing = {})
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            SettingsOptionChip(
                label = option.label,
                selected = selectedValue == option.value,
                onClick = { onOptionSelected(option.value) },
            )
        }
    }
}

@Composable
private fun SettingsOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(settingsRowColor(selected))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) settingsControlColor() else if (dark) Color.White.copy(alpha = 0.74f) else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingsSliderRow(
    title: String,
    detail: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val controlColor = settingsControlColor()
    var localValue by rememberSaveable { mutableFloatStateOf(value) }
    LaunchedEffect(value) { localValue = value }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    color = if (dark) Color.White.copy(alpha = 0.94f) else scheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    color = if (dark) Color.White.copy(alpha = 0.72f) else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            Slider(
                value = localValue,
                onValueChange = { localValue = it },
                onValueChangeFinished = { onValueChange(localValue) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = controlColor.copy(alpha = if (dark) 0.92f else 0.78f),
                    activeTrackColor = controlColor.copy(alpha = if (dark) 0.46f else 0.40f),
                    inactiveTrackColor = if (dark) Color.White.copy(alpha = 0.12f) else scheme.outlineVariant.copy(alpha = 0.46f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                thumb = {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(controlColor.copy(alpha = if (dark) 0.92f else 0.72f)),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(4.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = controlColor.copy(alpha = if (dark) 0.38f else 0.34f),
                            inactiveTrackColor = if (dark) Color.White.copy(alpha = 0.10f) else scheme.outlineVariant.copy(alpha = 0.40f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun SettingsRowShell(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                color = if (dark) Color.White.copy(alpha = 0.94f) else scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                color = if (dark) Color.White.copy(alpha = 0.70f) else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

@Composable
private fun SettingsStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = if (dark) Color.White.copy(alpha = 0.70f) else scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(value, color = if (dark) Color.White.copy(alpha = 0.94f) else scheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun usbExclusiveDetail(status: EchoPlaybackStatus): String {
    val diagnostics = status.diagnostics
    return when {
        diagnostics.usbBitPerfectActive -> stringResource(R.string.settings_usb_bit_perfect)
        diagnostics.usbAudioHasIsochronousOut -> stringResource(
            R.string.settings_usb_iso,
            diagnostics.usbAudioEndpointSummary ?: "iso OUT",
        )
        diagnostics.usbHostPermissionGranted -> stringResource(R.string.settings_usb_granted)
        diagnostics.usbHostPermissionPending -> stringResource(R.string.settings_usb_pending)
        diagnostics.usbBitPerfectSupported -> stringResource(R.string.settings_usb_supported)
        diagnostics.usbConnected -> stringResource(R.string.settings_usb_mixer)
        else -> stringResource(R.string.settings_usb_fallback)
    }
}

@Composable
private fun usbExclusiveTestDetail(status: EchoPlaybackStatus, result: String): String {
    val diagnostics = status.diagnostics
    return when {
        !diagnostics.usbConnected -> stringResource(R.string.settings_usb_not_detected)
        !diagnostics.usbHostPermissionGranted -> stringResource(R.string.settings_usb_need_permission)
        else -> result
    }
}
