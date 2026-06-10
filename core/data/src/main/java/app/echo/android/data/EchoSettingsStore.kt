package app.echo.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.echo.android.model.library.NeteaseAudioQuality
import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerPresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.echoSettings by preferencesDataStore(name = "echo-settings")

data class EchoAppSettings(
    val preferOffload: Boolean = true,
    val lastOutputRoute: String = "system",
    val dynamicArtworkEnabled: Boolean = true,
    val compactModeEnabled: Boolean = false,
    val pcHandoffEnabled: Boolean = true,
    val showLyricsControlDeck: Boolean = false,
    val onlineLyricsEnabled: Boolean = false,
    val usbExclusiveEnabled: Boolean = false,
    val usbExclusiveAutoRequestOnStartup: Boolean = true,
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String = EchoEqualizerPreset.Flat,
    val equalizerBandGains: List<Float> = EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Flat),
    val customBackgroundMode: String = EchoBackgroundMode.Default,
    val customBackgroundUri: String? = null,
    val customBackgroundBlur: Float = 24f,
    val customBackgroundBrightness: Float = 0.88f,
    val customBackgroundGlass: Float = 0.42f,
    val uiFontFamily: String = EchoFontFamilyMode.Outfit,
    val uiFontScale: Float = 1f,
    val uiDensityScale: Float = 1f,
    val lyricsFontFamily: String = EchoFontFamilyMode.Outfit,
    val lyricsFontScale: Float = 1f,
    val lyricsColorMode: String = EchoLyricsColorMode.White,
    val lyricsShowTranslation: Boolean = true,
    val lyricsShowRomanization: Boolean = true,
    val lyricsFocusGlowEnabled: Boolean = true,
    val importedFontUri: String? = null,
    val themeMode: String = EchoThemeMode.System,
    val scheduledDarkModeEnabled: Boolean = false,
    val scheduledDarkStartMinute: Int = 22 * 60,
    val scheduledDarkEndMinute: Int = 7 * 60,
    val lastFmEnabled: Boolean = false,
    val lastFmApiKey: String? = null,
    val lastFmSharedSecret: String? = null,
    val lastFmUsername: String? = null,
    val lastFmSessionKey: String? = null,
    val discordPresenceViaPcEnabled: Boolean = false,
    val echoLinkPcAddress: String? = null,
    val echoLinkPcToken: String? = null,
    val echoLinkAutoReconnectEnabled: Boolean = true,
    val echoLinkPreferLinkedLibrary: Boolean = true,
    val subsonicServerUrl: String? = null,
    val subsonicUsername: String? = null,
    val subsonicPassword: String? = null,
    val webDavServerUrl: String? = null,
    val webDavUsername: String? = null,
    val webDavPassword: String? = null,
    val neteaseUserId: Long? = null,
    val neteaseNickname: String? = null,
    val neteaseCookie: String? = null,
    val neteaseAudioQuality: String = NeteaseAudioQuality.Default.id,
)

object EchoBackgroundMode {
    const val Default = "default"
    const val Image = "image"
    const val Video = "video"
}

object EchoFontFamilyMode {
    const val System = "system"
    const val Outfit = "outfit"
    const val Serif = "serif"
    const val Monospace = "monospace"
    const val Imported = "imported"
}

object EchoLyricsColorMode {
    const val White = "white"
    const val Warm = "warm"
    const val Blue = "blue"
    const val Violet = "violet"
    const val Mint = "mint"
}

object EchoThemeMode {
    const val System = "system"
    const val Light = "light"
    const val Dark = "dark"
}

class EchoSettingsStore(
    private val context: Context,
) {
    val appSettings: Flow<EchoAppSettings> =
        context.echoSettings.data.map { preferences ->
            EchoAppSettings(
                preferOffload = preferences[Keys.PreferOffload] ?: true,
                lastOutputRoute = preferences[Keys.LastOutputRoute] ?: "system",
                dynamicArtworkEnabled = preferences[Keys.DynamicArtworkEnabled] ?: true,
                compactModeEnabled = preferences[Keys.CompactModeEnabled] ?: false,
                pcHandoffEnabled = preferences[Keys.PcHandoffEnabled] ?: true,
                showLyricsControlDeck = preferences[Keys.ShowLyricsControlDeck] ?: false,
                onlineLyricsEnabled = preferences[Keys.OnlineLyricsEnabled] ?: false,
                usbExclusiveEnabled = preferences[Keys.UsbExclusiveEnabled] ?: false,
                usbExclusiveAutoRequestOnStartup = preferences[Keys.UsbExclusiveAutoRequestOnStartup] ?: true,
                equalizerEnabled = preferences[Keys.EqualizerEnabled] ?: false,
                equalizerPreset = EchoEqualizerPresets.normalizePresetId(preferences[Keys.EqualizerPreset]),
                equalizerBandGains = parseEqualizerBandGains(
                    preferences[Keys.EqualizerBandGains],
                    EchoEqualizerPresets.normalizePresetId(preferences[Keys.EqualizerPreset]),
                ),
                customBackgroundMode = preferences[Keys.CustomBackgroundMode] ?: EchoBackgroundMode.Default,
                customBackgroundUri = preferences[Keys.CustomBackgroundUri],
                customBackgroundBlur = (preferences[Keys.CustomBackgroundBlur] ?: 24f).coerceIn(0f, 80f),
                customBackgroundBrightness = (preferences[Keys.CustomBackgroundBrightness] ?: 0.88f).coerceIn(0.35f, 1.15f),
                customBackgroundGlass = (preferences[Keys.CustomBackgroundGlass] ?: 0.42f).coerceIn(0.08f, 0.90f),
                uiFontFamily = preferences[Keys.UiFontFamily] ?: EchoFontFamilyMode.Outfit,
                uiFontScale = (preferences[Keys.UiFontScale] ?: 1f).coerceIn(0.88f, 1.18f),
                uiDensityScale = (preferences[Keys.UiDensityScale] ?: 1f).coerceIn(0.90f, 1.12f),
                lyricsFontFamily = preferences[Keys.LyricsFontFamily] ?: EchoFontFamilyMode.Outfit,
                lyricsFontScale = (preferences[Keys.LyricsFontScale] ?: 1f).coerceIn(0.82f, 1.28f),
                lyricsColorMode = preferences[Keys.LyricsColorMode] ?: EchoLyricsColorMode.White,
                lyricsShowTranslation = preferences[Keys.LyricsShowTranslation] ?: true,
                lyricsShowRomanization = preferences[Keys.LyricsShowRomanization] ?: true,
                lyricsFocusGlowEnabled = preferences[Keys.LyricsFocusGlowEnabled] ?: true,
                importedFontUri = preferences[Keys.ImportedFontUri],
                themeMode = preferences[Keys.ThemeMode] ?: EchoThemeMode.System,
                scheduledDarkModeEnabled = preferences[Keys.ScheduledDarkModeEnabled] ?: false,
                scheduledDarkStartMinute = (preferences[Keys.ScheduledDarkStartMinute] ?: 22 * 60).coerceIn(0, 23 * 60 + 59),
                scheduledDarkEndMinute = (preferences[Keys.ScheduledDarkEndMinute] ?: 7 * 60).coerceIn(0, 23 * 60 + 59),
                lastFmEnabled = preferences[Keys.LastFmEnabled] ?: false,
                lastFmApiKey = preferences[Keys.LastFmApiKey],
                lastFmSharedSecret = preferences[Keys.LastFmSharedSecret],
                lastFmUsername = preferences[Keys.LastFmUsername],
                lastFmSessionKey = preferences[Keys.LastFmSessionKey],
                discordPresenceViaPcEnabled = preferences[Keys.DiscordPresenceViaPcEnabled] ?: false,
                echoLinkPcAddress = preferences[Keys.EchoLinkPcAddress],
                echoLinkPcToken = preferences[Keys.EchoLinkPcToken],
                echoLinkAutoReconnectEnabled = preferences[Keys.EchoLinkAutoReconnectEnabled] ?: true,
                echoLinkPreferLinkedLibrary = preferences[Keys.EchoLinkPreferLinkedLibrary] ?: true,
                subsonicServerUrl = preferences[Keys.SubsonicServerUrl],
                subsonicUsername = preferences[Keys.SubsonicUsername],
                subsonicPassword = preferences[Keys.SubsonicPassword],
                webDavServerUrl = preferences[Keys.WebDavServerUrl],
                webDavUsername = preferences[Keys.WebDavUsername],
                webDavPassword = preferences[Keys.WebDavPassword],
                neteaseUserId = preferences[Keys.NeteaseUserId]?.toLongOrNull(),
                neteaseNickname = preferences[Keys.NeteaseNickname],
                neteaseCookie = preferences[Keys.NeteaseCookie],
                neteaseAudioQuality = NeteaseAudioQuality.fromId(preferences[Keys.NeteaseAudioQuality]).id,
            )
        }

    suspend fun setPreferOffload(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.PreferOffload] = enabled }
    }

    suspend fun setLastOutputRoute(route: String) {
        context.echoSettings.edit { it[Keys.LastOutputRoute] = route }
    }

    suspend fun setDynamicArtworkEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.DynamicArtworkEnabled] = enabled }
    }

    suspend fun setCompactModeEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.CompactModeEnabled] = enabled }
    }

    suspend fun setPcHandoffEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.PcHandoffEnabled] = enabled }
    }

    suspend fun setShowLyricsControlDeck(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.ShowLyricsControlDeck] = enabled }
    }

    suspend fun setOnlineLyricsEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.OnlineLyricsEnabled] = enabled }
    }

    suspend fun setUsbExclusiveEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.UsbExclusiveEnabled] = enabled }
    }

    suspend fun setUsbExclusiveAutoRequestOnStartup(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.UsbExclusiveAutoRequestOnStartup] = enabled }
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.EqualizerEnabled] = enabled }
    }

    suspend fun setEqualizerPreset(presetId: String) {
        val safePresetId = EchoEqualizerPresets.normalizePresetId(presetId)
        context.echoSettings.edit {
            it[Keys.EqualizerPreset] = safePresetId
            it[Keys.EqualizerBandGains] = formatEqualizerBandGains(
                EchoEqualizerPresets.gainsForPreset(safePresetId),
            )
        }
    }

    suspend fun setEqualizerBandGains(gainsDb: List<Float>) {
        context.echoSettings.edit {
            it[Keys.EqualizerPreset] = EchoEqualizerPreset.Custom
            it[Keys.EqualizerBandGains] = formatEqualizerBandGains(gainsDb)
        }
    }

    suspend fun resetEqualizer() {
        context.echoSettings.edit {
            it[Keys.EqualizerPreset] = EchoEqualizerPreset.Flat
            it[Keys.EqualizerBandGains] = formatEqualizerBandGains(
                EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Flat),
            )
        }
    }

    suspend fun setCustomBackground(mode: String, uri: String?) {
        context.echoSettings.edit {
            it[Keys.CustomBackgroundMode] = mode
            if (uri.isNullOrBlank()) {
                it.remove(Keys.CustomBackgroundUri)
            } else {
                it[Keys.CustomBackgroundUri] = uri
            }
        }
    }

    suspend fun setCustomBackgroundBlur(value: Float) {
        context.echoSettings.edit { it[Keys.CustomBackgroundBlur] = value.coerceIn(0f, 80f) }
    }

    suspend fun setCustomBackgroundBrightness(value: Float) {
        context.echoSettings.edit { it[Keys.CustomBackgroundBrightness] = value.coerceIn(0.35f, 1.15f) }
    }

    suspend fun setCustomBackgroundGlass(value: Float) {
        context.echoSettings.edit { it[Keys.CustomBackgroundGlass] = value.coerceIn(0.08f, 0.90f) }
    }

    suspend fun setUiFontFamily(value: String) {
        context.echoSettings.edit { it[Keys.UiFontFamily] = value }
    }

    suspend fun setUiFontScale(value: Float) {
        context.echoSettings.edit { it[Keys.UiFontScale] = value.coerceIn(0.88f, 1.18f) }
    }

    suspend fun setUiDensityScale(value: Float) {
        context.echoSettings.edit { it[Keys.UiDensityScale] = value.coerceIn(0.90f, 1.12f) }
    }

    suspend fun setLyricsFontFamily(value: String) {
        context.echoSettings.edit { it[Keys.LyricsFontFamily] = value }
    }

    suspend fun setLyricsFontScale(value: Float) {
        context.echoSettings.edit { it[Keys.LyricsFontScale] = value.coerceIn(0.82f, 1.28f) }
    }

    suspend fun setLyricsColorMode(value: String) {
        context.echoSettings.edit { it[Keys.LyricsColorMode] = value }
    }

    suspend fun setLyricsShowTranslation(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.LyricsShowTranslation] = enabled }
    }

    suspend fun setLyricsShowRomanization(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.LyricsShowRomanization] = enabled }
    }

    suspend fun setLyricsFocusGlowEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.LyricsFocusGlowEnabled] = enabled }
    }

    suspend fun setImportedFontUri(uri: String?) {
        context.echoSettings.edit {
            if (uri.isNullOrBlank()) {
                it.remove(Keys.ImportedFontUri)
                if (it[Keys.UiFontFamily] == EchoFontFamilyMode.Imported) {
                    it[Keys.UiFontFamily] = EchoFontFamilyMode.Outfit
                }
                if (it[Keys.LyricsFontFamily] == EchoFontFamilyMode.Imported) {
                    it[Keys.LyricsFontFamily] = EchoFontFamilyMode.Outfit
                }
            } else {
                it[Keys.ImportedFontUri] = uri
            }
        }
    }

    suspend fun setThemeMode(value: String) {
        context.echoSettings.edit { it[Keys.ThemeMode] = value }
    }

    suspend fun setScheduledDarkModeEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.ScheduledDarkModeEnabled] = enabled }
    }

    suspend fun setScheduledDarkStartMinute(value: Int) {
        context.echoSettings.edit { it[Keys.ScheduledDarkStartMinute] = value.coerceIn(0, 23 * 60 + 59) }
    }

    suspend fun setScheduledDarkEndMinute(value: Int) {
        context.echoSettings.edit { it[Keys.ScheduledDarkEndMinute] = value.coerceIn(0, 23 * 60 + 59) }
    }

    suspend fun setLastFmEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.LastFmEnabled] = enabled }
    }

    suspend fun setLastFmCredentials(
        apiKey: String,
        sharedSecret: String,
        username: String,
        sessionKey: String,
    ) {
        context.echoSettings.edit {
            it[Keys.LastFmApiKey] = apiKey.trim()
            it[Keys.LastFmSharedSecret] = sharedSecret.trim()
            it[Keys.LastFmUsername] = username.trim()
            it[Keys.LastFmSessionKey] = sessionKey.trim()
            it[Keys.LastFmEnabled] = true
        }
    }

    suspend fun clearLastFmCredentials() {
        context.echoSettings.edit {
            it[Keys.LastFmEnabled] = false
            it.remove(Keys.LastFmUsername)
            it.remove(Keys.LastFmSessionKey)
        }
    }

    suspend fun setDiscordPresenceViaPcEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.DiscordPresenceViaPcEnabled] = enabled }
    }

    suspend fun setEchoLinkPcEndpoint(
        address: String,
        token: String,
    ) {
        context.echoSettings.edit {
            val safeAddress = address.trim().trimEnd('/')
            val safeToken = token.trim()
            if (safeAddress.isBlank() || safeToken.isBlank()) {
                it.remove(Keys.EchoLinkPcAddress)
                it.remove(Keys.EchoLinkPcToken)
            } else {
                it[Keys.EchoLinkPcAddress] = safeAddress
                it[Keys.EchoLinkPcToken] = safeToken
            }
        }
    }

    suspend fun setEchoLinkAutoReconnectEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.EchoLinkAutoReconnectEnabled] = enabled }
    }

    suspend fun setEchoLinkPreferLinkedLibrary(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.EchoLinkPreferLinkedLibrary] = enabled }
    }

    suspend fun clearEchoLinkPcEndpoint() {
        context.echoSettings.edit {
            it.remove(Keys.EchoLinkPcAddress)
            it.remove(Keys.EchoLinkPcToken)
        }
    }

    suspend fun setSubsonicCredentials(
        serverUrl: String,
        username: String,
        password: String,
    ) {
        context.echoSettings.edit {
            val normalizedUrl = serverUrl.trim().trimEnd('/')
            if (normalizedUrl.isBlank() || username.isBlank() || password.isBlank()) {
                it.remove(Keys.SubsonicServerUrl)
                it.remove(Keys.SubsonicUsername)
                it.remove(Keys.SubsonicPassword)
            } else {
                it[Keys.SubsonicServerUrl] = normalizedUrl
                it[Keys.SubsonicUsername] = username.trim()
                it[Keys.SubsonicPassword] = password
            }
        }
    }

    suspend fun clearSubsonicCredentials() {
        context.echoSettings.edit {
            it.remove(Keys.SubsonicServerUrl)
            it.remove(Keys.SubsonicUsername)
            it.remove(Keys.SubsonicPassword)
        }
    }

    suspend fun setWebDavCredentials(
        serverUrl: String,
        username: String,
        password: String,
    ) {
        context.echoSettings.edit {
            val normalizedUrl = serverUrl.trim().trimEnd('/')
            if (normalizedUrl.isBlank() || username.isBlank() || password.isBlank()) {
                it.remove(Keys.WebDavServerUrl)
                it.remove(Keys.WebDavUsername)
                it.remove(Keys.WebDavPassword)
            } else {
                it[Keys.WebDavServerUrl] = normalizedUrl
                it[Keys.WebDavUsername] = username.trim()
                it[Keys.WebDavPassword] = password
            }
        }
    }

    suspend fun clearWebDavCredentials() {
        context.echoSettings.edit {
            it.remove(Keys.WebDavServerUrl)
            it.remove(Keys.WebDavUsername)
            it.remove(Keys.WebDavPassword)
        }
    }

    suspend fun setNeteaseSession(
        userId: Long,
        nickname: String,
        cookie: String,
    ) {
        context.echoSettings.edit {
            it[Keys.NeteaseUserId] = userId.toString()
            it[Keys.NeteaseNickname] = nickname.trim()
            it[Keys.NeteaseCookie] = cookie
        }
    }

    suspend fun clearNeteaseSession() {
        context.echoSettings.edit {
            it.remove(Keys.NeteaseUserId)
            it.remove(Keys.NeteaseNickname)
            it.remove(Keys.NeteaseCookie)
        }
    }

    suspend fun setNeteaseAudioQuality(qualityId: String) {
        context.echoSettings.edit {
            it[Keys.NeteaseAudioQuality] = NeteaseAudioQuality.fromId(qualityId).id
        }
    }

    private object Keys {
        val PreferOffload = booleanPreferencesKey("prefer_offload")
        val LastOutputRoute = stringPreferencesKey("last_output_route")
        val DynamicArtworkEnabled = booleanPreferencesKey("dynamic_artwork_enabled")
        val CompactModeEnabled = booleanPreferencesKey("compact_mode_enabled")
        val PcHandoffEnabled = booleanPreferencesKey("pc_handoff_enabled")
        val ShowLyricsControlDeck = booleanPreferencesKey("show_lyrics_control_deck")
        val OnlineLyricsEnabled = booleanPreferencesKey("online_lyrics_enabled")
        val UsbExclusiveEnabled = booleanPreferencesKey("usb_exclusive_enabled")
        val UsbExclusiveAutoRequestOnStartup = booleanPreferencesKey("usb_exclusive_auto_request_on_startup")
        val EqualizerEnabled = booleanPreferencesKey("equalizer_enabled")
        val EqualizerPreset = stringPreferencesKey("equalizer_preset")
        val EqualizerBandGains = stringPreferencesKey("equalizer_band_gains")
        val CustomBackgroundMode = stringPreferencesKey("custom_background_mode")
        val CustomBackgroundUri = stringPreferencesKey("custom_background_uri")
        val CustomBackgroundBlur = floatPreferencesKey("custom_background_blur")
        val CustomBackgroundBrightness = floatPreferencesKey("custom_background_brightness")
        val CustomBackgroundGlass = floatPreferencesKey("custom_background_glass")
        val UiFontFamily = stringPreferencesKey("ui_font_family")
        val UiFontScale = floatPreferencesKey("ui_font_scale")
        val UiDensityScale = floatPreferencesKey("ui_density_scale")
        val LyricsFontFamily = stringPreferencesKey("lyrics_font_family")
        val LyricsFontScale = floatPreferencesKey("lyrics_font_scale")
        val LyricsColorMode = stringPreferencesKey("lyrics_color_mode")
        val LyricsShowTranslation = booleanPreferencesKey("lyrics_show_translation")
        val LyricsShowRomanization = booleanPreferencesKey("lyrics_show_romanization")
        val LyricsFocusGlowEnabled = booleanPreferencesKey("lyrics_focus_glow_enabled")
        val ImportedFontUri = stringPreferencesKey("imported_font_uri")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val ScheduledDarkModeEnabled = booleanPreferencesKey("scheduled_dark_mode_enabled")
        val ScheduledDarkStartMinute = intPreferencesKey("scheduled_dark_start_minute")
        val ScheduledDarkEndMinute = intPreferencesKey("scheduled_dark_end_minute")
        val LastFmEnabled = booleanPreferencesKey("lastfm_enabled")
        val LastFmApiKey = stringPreferencesKey("lastfm_api_key")
        val LastFmSharedSecret = stringPreferencesKey("lastfm_shared_secret")
        val LastFmUsername = stringPreferencesKey("lastfm_username")
        val LastFmSessionKey = stringPreferencesKey("lastfm_session_key")
        val DiscordPresenceViaPcEnabled = booleanPreferencesKey("discord_presence_via_pc_enabled")
        val EchoLinkPcAddress = stringPreferencesKey("echo_link_pc_address")
        val EchoLinkPcToken = stringPreferencesKey("echo_link_pc_token")
        val EchoLinkAutoReconnectEnabled = booleanPreferencesKey("echo_link_auto_reconnect_enabled")
        val EchoLinkPreferLinkedLibrary = booleanPreferencesKey("echo_link_prefer_linked_library")
        val SubsonicServerUrl = stringPreferencesKey("subsonic_server_url")
        val SubsonicUsername = stringPreferencesKey("subsonic_username")
        val SubsonicPassword = stringPreferencesKey("subsonic_password")
        val WebDavServerUrl = stringPreferencesKey("webdav_server_url")
        val WebDavUsername = stringPreferencesKey("webdav_username")
        val WebDavPassword = stringPreferencesKey("webdav_password")
        val NeteaseUserId = stringPreferencesKey("netease_user_id")
        val NeteaseNickname = stringPreferencesKey("netease_nickname")
        val NeteaseCookie = stringPreferencesKey("netease_cookie")
        val NeteaseAudioQuality = stringPreferencesKey("netease_audio_quality")
    }
}

private fun parseEqualizerBandGains(value: String?, presetId: String): List<Float> {
    val parsed = value
        ?.split(',')
        ?.mapNotNull { raw -> raw.trim().toFloatOrNull()?.coerceIn(-18f, 18f) }
        .orEmpty()
    return parsed.ifEmpty { EchoEqualizerPresets.gainsForPreset(presetId) }
}

private fun formatEqualizerBandGains(gainsDb: List<Float>): String =
    gainsDb.joinToString(",") { value ->
        ((value.coerceIn(-18f, 18f) * 10f).toInt() / 10f).toString()
    }
