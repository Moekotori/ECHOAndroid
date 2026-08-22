package app.echo.android.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerPresets
import app.echo.android.model.playback.OpraEqBand
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.settings.EchoAppLanguage
import app.echo.android.model.settings.EchoPerformanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private val Context.echoSettings by preferencesDataStore(name = "echo-settings")
private val Context.echoPlaybackResumeSettings by preferencesDataStore(name = "echo-playback-resume")
private val PlaybackResumeKey = stringPreferencesKey("playback_resume")
private const val DefaultNeteaseAudioQuality = "lossless"
private const val RemotePlaybackAuthSnapshotTimeoutMillis = 1_000L

data class EchoAppSettings(
    val preferOffload: Boolean = true,
    val lastOutputRoute: String = "system",
    val dynamicArtworkEnabled: Boolean = true,
    val compactModeEnabled: Boolean = false,
    val dynamicColorEnabled: Boolean = false,
    val playbackHapticsEnabled: Boolean = true,
    val performanceMode: String = EchoPerformanceMode.Auto.id,
    val trackAudioInfoTagsVisible: Boolean = true,
    val pcHandoffEnabled: Boolean = true,
    val showLyricsControlDeck: Boolean = false,
    val onlineLyricsEnabled: Boolean = false,
    val usbExclusiveEnabled: Boolean = false,
    val usbExclusiveAutoRequestOnStartup: Boolean = true,
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String = EchoEqualizerPreset.Flat,
    val equalizerBandGains: List<Float> = EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Flat),
    val equalizerPreampDb: Float = 0f,
    val equalizerParametric: Boolean = false,
    val equalizerSourceLabel: String? = null,
    val equalizerFilters: List<OpraEqBand> = emptyList(),
    val customBackgroundMode: String = EchoBackgroundMode.Default,
    val customBackgroundUri: String? = null,
    val customBackgroundBlur: Float = 24f,
    val customBackgroundBrightness: Float = 0.88f,
    val customBackgroundGlass: Float = 0.42f,
    val customBackgroundScale: Float = 1.05f,
    val uiFontFamily: String = EchoFontFamilyMode.System,
    val uiFontScale: Float = 1f,
    val uiDensityScale: Float = 1f,
    val lyricsFontFamily: String = EchoFontFamilyMode.System,
    val lyricsFontScale: Float = 1f,
    val lyricsColorMode: String = EchoLyricsColorMode.White,
    val lyricsAlignment: String = EchoLyricsAlignment.Center,
    val lyricsLineSpacing: Float = 1f,
    val lyricsBackgroundDim: Float = 0f,
    val lyricsWordHighlightEnabled: Boolean = true,
    val lyricsWordHighlightIntensity: Float = 1f,
    val lyricsImmersiveModeEnabled: Boolean = false,
    val lyricsMotionMode: String = EchoLyricsMotionMode.Smooth,
    val lyricsShowTranslation: Boolean = true,
    val lyricsShowRomanization: Boolean = true,
    val lyricsFocusGlowEnabled: Boolean = false,
    val importedFontUri: String? = null,
    val themeMode: String = EchoThemeMode.Dark,
    val appLanguage: String = EchoAppLanguage.System,
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
    val librarySelectedSource: String = EchoLibrarySelectedSource.Local,
    val subsonicServerUrl: String? = null,
    val subsonicUsername: String? = null,
    val subsonicPassword: String? = null,
    val webDavServerUrl: String? = null,
    val webDavUsername: String? = null,
    val webDavPassword: String? = null,
    val neteaseUserId: Long? = null,
    val neteaseNickname: String? = null,
    val neteaseCookie: String? = null,
    val neteaseAudioQuality: String = DefaultNeteaseAudioQuality,
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

object EchoLyricsAlignment {
    const val Start = "start"
    const val Center = "center"
    const val Dynamic = "dynamic"
}

object EchoLyricsMotionMode {
    const val Calm = "calm"
    const val Smooth = "smooth"
    const val Stage = "stage"
}

object EchoThemeMode {
    const val System = "system"
    const val Light = "light"
    const val Dark = "dark"
}

object EchoLibrarySelectedSource {
    const val Local = "local"
    const val PcEcho = "pc_echo"
    const val Cloud = "cloud"
}

data class EchoSavedPlaybackSession(
    val queue: List<EchoTrackRef>,
    val currentIndex: Int,
    val positionMs: Long,
    val playWhenReady: Boolean,
    val shuffleEnabled: Boolean = false,
    val repeatMode: EchoRepeatMode = EchoRepeatMode.Off,
    val playbackSpeed: Float = 1f,
    val playbackPitch: Float = 1f,
)

class EchoSettingsStore(
    private val context: Context,
) {
    @Volatile
    private var cachedStartupThemeSnapshot: EchoStartupThemeSnapshot? = null
    @Volatile
    private var cachedPlaybackQueueIdentity: String? = null
    @Volatile
    private var playbackQueueIdentityLoaded: Boolean = false

    fun startupAppSettingsSnapshot(): EchoAppSettings =
        context.readEchoStartupThemeSnapshot().toAppSettings()

    fun remotePlaybackAuthSettingsSnapshot(
        timeoutMillis: Long = RemotePlaybackAuthSnapshotTimeoutMillis,
    ): EchoAppSettings? = runBlocking(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMillis) {
            appSettings.first()
        }
    }

    fun cacheStartupThemeSnapshot(
        settings: EchoAppSettings,
        synchronous: Boolean = false,
    ) {
        cacheStartupThemeSnapshot(settings.toStartupThemeSnapshot(), synchronous)
    }

    val appSettings: Flow<EchoAppSettings> =
        context.echoSettings.data.map { preferences ->
            EchoAppSettings(
                preferOffload = preferences[Keys.PreferOffload] ?: true,
                lastOutputRoute = preferences[Keys.LastOutputRoute] ?: "system",
                dynamicArtworkEnabled = preferences[Keys.DynamicArtworkEnabled] ?: true,
                compactModeEnabled = preferences[Keys.CompactModeEnabled] ?: false,
                dynamicColorEnabled = preferences[Keys.DynamicColorEnabled] ?: false,
                playbackHapticsEnabled = preferences[Keys.PlaybackHapticsEnabled] ?: true,
                performanceMode = EchoPerformanceMode.fromId(preferences[Keys.PerformanceMode]).id,
                trackAudioInfoTagsVisible = preferences[Keys.TrackAudioInfoTagsVisible] ?: true,
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
                equalizerPreampDb = (preferences[Keys.EqualizerPreampDb] ?: 0f).coerceIn(-24f, 12f),
                equalizerParametric = preferences[Keys.EqualizerParametric] ?: false,
                equalizerSourceLabel = preferences[Keys.EqualizerSourceLabel],
                equalizerFilters = parseEqualizerFilters(preferences[Keys.EqualizerFilters]),
                customBackgroundMode = preferences[Keys.CustomBackgroundMode] ?: EchoBackgroundMode.Default,
                customBackgroundUri = preferences[Keys.CustomBackgroundUri],
                customBackgroundBlur = (preferences[Keys.CustomBackgroundBlur] ?: 24f).coerceIn(0f, 80f),
                customBackgroundBrightness = (preferences[Keys.CustomBackgroundBrightness] ?: 0.88f).coerceIn(0.35f, 1.15f),
                customBackgroundGlass = (preferences[Keys.CustomBackgroundGlass] ?: 0.42f).coerceIn(0.08f, 0.90f),
                customBackgroundScale = (preferences[Keys.CustomBackgroundScale] ?: 1.05f).coerceIn(1.00f, 1.40f),
                uiFontFamily = normalizeFontFamilyMode(preferences[Keys.UiFontFamily]),
                uiFontScale = (preferences[Keys.UiFontScale] ?: 1f).coerceIn(0.88f, 1.18f),
                uiDensityScale = (preferences[Keys.UiDensityScale] ?: 1f).coerceIn(0.90f, 1.12f),
                lyricsFontFamily = normalizeFontFamilyMode(preferences[Keys.LyricsFontFamily]),
                lyricsFontScale = (preferences[Keys.LyricsFontScale] ?: 1f).coerceIn(0.82f, 1.28f),
                lyricsColorMode = preferences[Keys.LyricsColorMode] ?: EchoLyricsColorMode.White,
                lyricsAlignment = normalizeLyricsAlignment(preferences[Keys.LyricsAlignment]),
                lyricsLineSpacing = (preferences[Keys.LyricsLineSpacing] ?: 1f).coerceIn(0.82f, 1.38f),
                lyricsBackgroundDim = (preferences[Keys.LyricsBackgroundDim] ?: 0f).coerceIn(0f, 0.78f),
                lyricsWordHighlightEnabled = preferences[Keys.LyricsWordHighlightEnabled] ?: true,
                lyricsWordHighlightIntensity = (preferences[Keys.LyricsWordHighlightIntensity] ?: 1f).coerceIn(0.45f, 1.35f),
                lyricsImmersiveModeEnabled = preferences[Keys.LyricsImmersiveModeEnabled] ?: false,
                lyricsMotionMode = normalizeLyricsMotionMode(preferences[Keys.LyricsMotionMode]),
                lyricsShowTranslation = preferences[Keys.LyricsShowTranslation] ?: true,
                lyricsShowRomanization = preferences[Keys.LyricsShowRomanization] ?: true,
                lyricsFocusGlowEnabled = preferences[Keys.LyricsFocusGlowEnabled] ?: false,
                importedFontUri = preferences[Keys.ImportedFontUri],
                themeMode = normalizeThemeMode(preferences[Keys.ThemeMode]),
                appLanguage = EchoAppLanguage.fromId(preferences[Keys.AppLanguage]),
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
                librarySelectedSource = normalizeLibrarySelectedSource(preferences[Keys.LibrarySelectedSource]),
                subsonicServerUrl = preferences[Keys.SubsonicServerUrl]
                    ?.let(::normalizeSubsonicBaseUrl)
                    ?.takeIf { it.isNotBlank() },
                subsonicUsername = preferences[Keys.SubsonicUsername],
                subsonicPassword = preferences[Keys.SubsonicPassword],
                webDavServerUrl = preferences[Keys.WebDavServerUrl],
                webDavUsername = preferences[Keys.WebDavUsername],
                webDavPassword = preferences[Keys.WebDavPassword],
                neteaseUserId = preferences[Keys.NeteaseUserId]?.toLongOrNull(),
                neteaseNickname = preferences[Keys.NeteaseNickname],
                neteaseCookie = preferences[Keys.NeteaseCookie],
                neteaseAudioQuality = preferences[Keys.NeteaseAudioQuality]?.takeIf { it.isNotBlank() }
                    ?: DefaultNeteaseAudioQuality,
            )
        }

    private fun cacheStartupThemeSnapshot(
        snapshot: EchoStartupThemeSnapshot,
        synchronous: Boolean = false,
    ) {
        if (!synchronous && cachedStartupThemeSnapshot == snapshot) return
        context.writeEchoStartupThemeSnapshot(snapshot, synchronous)
        cachedStartupThemeSnapshot = snapshot
    }

    private fun currentStartupThemeSnapshot(): EchoStartupThemeSnapshot =
        cachedStartupThemeSnapshot ?: context.readEchoStartupThemeSnapshot()

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

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.DynamicColorEnabled] = enabled }
    }

    suspend fun setPlaybackHapticsEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.PlaybackHapticsEnabled] = enabled }
    }

    suspend fun setPerformanceMode(value: String) {
        context.echoSettings.edit { it[Keys.PerformanceMode] = EchoPerformanceMode.fromId(value).id }
    }

    suspend fun setTrackAudioInfoTagsVisible(visible: Boolean) {
        context.echoSettings.edit { it[Keys.TrackAudioInfoTagsVisible] = visible }
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
            clearEqualizerParametric(it)
        }
    }

    suspend fun setEqualizerBandGains(gainsDb: List<Float>) {
        context.echoSettings.edit {
            it[Keys.EqualizerPreset] = EchoEqualizerPreset.Custom
            it[Keys.EqualizerBandGains] = formatEqualizerBandGains(gainsDb)
            clearEqualizerParametric(it)
        }
    }

    suspend fun setEqualizerParametricConfig(
        gainsDb: List<Float>,
        preampDb: Float,
        filters: List<OpraEqBand>,
        sourceLabel: String?,
    ) {
        context.echoSettings.edit {
            it[Keys.EqualizerEnabled] = true
            it[Keys.EqualizerPreset] = EchoEqualizerPreset.Custom
            it[Keys.EqualizerBandGains] = formatEqualizerBandGains(gainsDb)
            it[Keys.EqualizerPreampDb] = preampDb.coerceIn(-24f, 12f)
            it[Keys.EqualizerParametric] = filters.isNotEmpty()
            it[Keys.EqualizerFilters] = formatEqualizerFilters(filters)
            if (sourceLabel.isNullOrBlank()) {
                it.remove(Keys.EqualizerSourceLabel)
            } else {
                it[Keys.EqualizerSourceLabel] = sourceLabel
            }
        }
    }

    suspend fun resetEqualizer() {
        context.echoSettings.edit {
            it[Keys.EqualizerPreset] = EchoEqualizerPreset.Flat
            it[Keys.EqualizerBandGains] = formatEqualizerBandGains(
                EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Flat),
            )
            clearEqualizerParametric(it)
        }
    }

    private fun clearEqualizerParametric(preferences: MutablePreferences) {
        preferences[Keys.EqualizerPreampDb] = 0f
        preferences[Keys.EqualizerParametric] = false
        preferences.remove(Keys.EqualizerSourceLabel)
        preferences.remove(Keys.EqualizerFilters)
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

    suspend fun setCustomBackgroundScale(value: Float) {
        context.echoSettings.edit { it[Keys.CustomBackgroundScale] = value.coerceIn(1.00f, 1.40f) }
    }

    suspend fun setUiFontFamily(value: String) {
        context.echoSettings.edit { it[Keys.UiFontFamily] = normalizeFontFamilyMode(value) }
    }

    suspend fun setUiFontScale(value: Float) {
        context.echoSettings.edit { it[Keys.UiFontScale] = value.coerceIn(0.88f, 1.18f) }
    }

    suspend fun setUiDensityScale(value: Float) {
        context.echoSettings.edit { it[Keys.UiDensityScale] = value.coerceIn(0.90f, 1.12f) }
    }

    suspend fun setLyricsFontFamily(value: String) {
        context.echoSettings.edit { it[Keys.LyricsFontFamily] = normalizeFontFamilyMode(value) }
    }

    suspend fun setLyricsFontScale(value: Float) {
        context.echoSettings.edit { it[Keys.LyricsFontScale] = value.coerceIn(0.82f, 1.28f) }
    }

    suspend fun setLyricsColorMode(value: String) {
        context.echoSettings.edit { it[Keys.LyricsColorMode] = value }
    }

    suspend fun setLyricsAlignment(value: String) {
        context.echoSettings.edit { it[Keys.LyricsAlignment] = normalizeLyricsAlignment(value) }
    }

    suspend fun setLyricsLineSpacing(value: Float) {
        context.echoSettings.edit { it[Keys.LyricsLineSpacing] = value.coerceIn(0.82f, 1.38f) }
    }

    suspend fun setLyricsBackgroundDim(value: Float) {
        context.echoSettings.edit { it[Keys.LyricsBackgroundDim] = value.coerceIn(0f, 0.78f) }
    }

    suspend fun setLyricsWordHighlightEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.LyricsWordHighlightEnabled] = enabled }
    }

    suspend fun setLyricsWordHighlightIntensity(value: Float) {
        context.echoSettings.edit { it[Keys.LyricsWordHighlightIntensity] = value.coerceIn(0.45f, 1.35f) }
    }

    suspend fun setLyricsImmersiveModeEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.LyricsImmersiveModeEnabled] = enabled }
    }

    suspend fun setLyricsMotionMode(value: String) {
        context.echoSettings.edit { it[Keys.LyricsMotionMode] = normalizeLyricsMotionMode(value) }
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
                    it[Keys.UiFontFamily] = EchoFontFamilyMode.System
                }
                if (it[Keys.LyricsFontFamily] == EchoFontFamilyMode.Imported) {
                    it[Keys.LyricsFontFamily] = EchoFontFamilyMode.System
                }
            } else {
                it[Keys.ImportedFontUri] = uri
            }
        }
    }

    suspend fun setThemeMode(value: String) {
        val safeValue = normalizeThemeMode(value)
        context.echoSettings.edit { it[Keys.ThemeMode] = safeValue }
        cacheStartupThemeSnapshot(
            currentStartupThemeSnapshot().copy(themeMode = safeValue),
            synchronous = true,
        )
    }

    fun persistAppLanguageSnapshot(value: String) {
        cacheStartupThemeSnapshot(
            currentStartupThemeSnapshot().copy(appLanguage = EchoAppLanguage.fromId(value)),
            synchronous = true,
        )
    }

    suspend fun setAppLanguage(value: String) {
        val safeValue = EchoAppLanguage.fromId(value)
        context.echoSettings.edit { it[Keys.AppLanguage] = safeValue }
        persistAppLanguageSnapshot(safeValue)
    }

    suspend fun setScheduledDarkModeEnabled(enabled: Boolean) {
        context.echoSettings.edit { it[Keys.ScheduledDarkModeEnabled] = enabled }
        cacheStartupThemeSnapshot(
            currentStartupThemeSnapshot().copy(scheduledDarkModeEnabled = enabled),
            synchronous = true,
        )
    }

    suspend fun setScheduledDarkStartMinute(value: Int) {
        val safeValue = value.coerceIn(0, 23 * 60 + 59)
        context.echoSettings.edit { it[Keys.ScheduledDarkStartMinute] = safeValue }
        cacheStartupThemeSnapshot(
            currentStartupThemeSnapshot().copy(scheduledDarkStartMinute = safeValue),
            synchronous = true,
        )
    }

    suspend fun setScheduledDarkEndMinute(value: Int) {
        val safeValue = value.coerceIn(0, 23 * 60 + 59)
        context.echoSettings.edit { it[Keys.ScheduledDarkEndMinute] = safeValue }
        cacheStartupThemeSnapshot(
            currentStartupThemeSnapshot().copy(scheduledDarkEndMinute = safeValue),
            synchronous = true,
        )
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

    suspend fun setLibrarySelectedSource(source: String) {
        context.echoSettings.edit {
            it[Keys.LibrarySelectedSource] = normalizeLibrarySelectedSource(source)
        }
    }

    suspend fun savePlaybackSession(session: EchoSavedPlaybackSession?) {
        if (session == null || session.queue.isEmpty() || session.currentIndex !in session.queue.indices) {
            context.echoSettings.edit { it.remove(Keys.LastPlaybackSession) }
            context.echoPlaybackResumeSettings.edit { it.remove(PlaybackResumeKey) }
            cachedPlaybackQueueIdentity = null
            playbackQueueIdentityLoaded = true
            return
        }

        val queueIdentity = session.playbackQueueIdentity()
        val savedQueueIdentity = if (playbackQueueIdentityLoaded) {
            cachedPlaybackQueueIdentity
        } else {
            context.echoSettings.data.first()[Keys.LastPlaybackSession]
                ?.let(::parsePlaybackSession)
                ?.playbackQueueIdentity()
                .also {
                    cachedPlaybackQueueIdentity = it
                    playbackQueueIdentityLoaded = true
                }
        }
        if (savedQueueIdentity != queueIdentity) {
            context.echoSettings.edit { it[Keys.LastPlaybackSession] = session.toPreferenceValue() }
            cachedPlaybackQueueIdentity = queueIdentity
            playbackQueueIdentityLoaded = true
        }
        context.echoPlaybackResumeSettings.edit {
            it[PlaybackResumeKey] = session.toResumePreferenceValue(queueIdentity)
        }
    }

    suspend fun getSavedPlaybackSession(): EchoSavedPlaybackSession? {
        val savedSession = context.echoSettings.data.first()[Keys.LastPlaybackSession]
            ?.let(::parsePlaybackSession)
            ?: return null
        cachedPlaybackQueueIdentity = savedSession.playbackQueueIdentity()
        playbackQueueIdentityLoaded = true
        val savedResume = context.echoPlaybackResumeSettings.data.first()[PlaybackResumeKey]
            ?.let(::parsePlaybackResume)
        return savedSession.withPlaybackResume(savedResume)
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
            val normalizedUrl = normalizeSubsonicBaseUrl(serverUrl)
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

    private object Keys {
        val PreferOffload = booleanPreferencesKey("prefer_offload")
        val LastOutputRoute = stringPreferencesKey("last_output_route")
        val DynamicArtworkEnabled = booleanPreferencesKey("dynamic_artwork_enabled")
        val CompactModeEnabled = booleanPreferencesKey("compact_mode_enabled")
        val DynamicColorEnabled = booleanPreferencesKey("dynamic_color_enabled")
        val PlaybackHapticsEnabled = booleanPreferencesKey("playback_haptics_enabled")
        val PerformanceMode = stringPreferencesKey("performance_mode")
        val TrackAudioInfoTagsVisible = booleanPreferencesKey("track_audio_info_tags_visible")
        val PcHandoffEnabled = booleanPreferencesKey("pc_handoff_enabled")
        val ShowLyricsControlDeck = booleanPreferencesKey("show_lyrics_control_deck")
        val OnlineLyricsEnabled = booleanPreferencesKey("online_lyrics_enabled")
        val UsbExclusiveEnabled = booleanPreferencesKey("usb_exclusive_enabled")
        val UsbExclusiveAutoRequestOnStartup = booleanPreferencesKey("usb_exclusive_auto_request_on_startup")
        val EqualizerEnabled = booleanPreferencesKey("equalizer_enabled")
        val EqualizerPreset = stringPreferencesKey("equalizer_preset")
        val EqualizerBandGains = stringPreferencesKey("equalizer_band_gains")
        val EqualizerPreampDb = floatPreferencesKey("equalizer_preamp_db")
        val EqualizerParametric = booleanPreferencesKey("equalizer_parametric")
        val EqualizerSourceLabel = stringPreferencesKey("equalizer_source_label")
        val EqualizerFilters = stringPreferencesKey("equalizer_filters")
        val CustomBackgroundMode = stringPreferencesKey("custom_background_mode")
        val CustomBackgroundUri = stringPreferencesKey("custom_background_uri")
        val CustomBackgroundBlur = floatPreferencesKey("custom_background_blur")
        val CustomBackgroundBrightness = floatPreferencesKey("custom_background_brightness")
        val CustomBackgroundGlass = floatPreferencesKey("custom_background_glass")
        val CustomBackgroundScale = floatPreferencesKey("custom_background_scale")
        val UiFontFamily = stringPreferencesKey("ui_font_family")
        val UiFontScale = floatPreferencesKey("ui_font_scale")
        val UiDensityScale = floatPreferencesKey("ui_density_scale")
        val LyricsFontFamily = stringPreferencesKey("lyrics_font_family")
        val LyricsFontScale = floatPreferencesKey("lyrics_font_scale")
        val LyricsColorMode = stringPreferencesKey("lyrics_color_mode")
        val LyricsAlignment = stringPreferencesKey("lyrics_alignment")
        val LyricsLineSpacing = floatPreferencesKey("lyrics_line_spacing")
        val LyricsBackgroundDim = floatPreferencesKey("lyrics_background_dim")
        val LyricsWordHighlightEnabled = booleanPreferencesKey("lyrics_word_highlight_enabled")
        val LyricsWordHighlightIntensity = floatPreferencesKey("lyrics_word_highlight_intensity")
        val LyricsImmersiveModeEnabled = booleanPreferencesKey("lyrics_immersive_mode_enabled")
        val LyricsMotionMode = stringPreferencesKey("lyrics_motion_mode")
        val LyricsShowTranslation = booleanPreferencesKey("lyrics_show_translation")
        val LyricsShowRomanization = booleanPreferencesKey("lyrics_show_romanization")
        val LyricsFocusGlowEnabled = booleanPreferencesKey("lyrics_focus_glow_enabled")
        val ImportedFontUri = stringPreferencesKey("imported_font_uri")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val AppLanguage = stringPreferencesKey("app_language")
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
        val LibrarySelectedSource = stringPreferencesKey("library_selected_source")
        val LastPlaybackSession = stringPreferencesKey("last_playback_session")
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

private fun normalizeFontFamilyMode(value: String?): String =
    when (value) {
        EchoFontFamilyMode.Serif,
        EchoFontFamilyMode.Monospace,
        EchoFontFamilyMode.Imported -> value
        else -> EchoFontFamilyMode.System
    }

private fun normalizeLyricsAlignment(value: String?): String =
    when (value) {
        EchoLyricsAlignment.Start,
        EchoLyricsAlignment.Center,
        EchoLyricsAlignment.Dynamic -> value
        else -> EchoLyricsAlignment.Center
    }

private fun normalizeLyricsMotionMode(value: String?): String =
    when (value) {
        EchoLyricsMotionMode.Calm,
        EchoLyricsMotionMode.Smooth,
        EchoLyricsMotionMode.Stage -> value
        else -> EchoLyricsMotionMode.Smooth
    }

private fun normalizeLibrarySelectedSource(value: String?): String =
    when (value) {
        EchoLibrarySelectedSource.PcEcho,
        EchoLibrarySelectedSource.Cloud -> value
        else -> EchoLibrarySelectedSource.Local
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

internal fun parseEqualizerFilters(value: String?): List<OpraEqBand> {
    if (value.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(value)
        List(array.length()) { index -> array.optJSONObject(index) }
            .mapNotNull { band ->
                val type = band?.optString("type")?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val frequency = band.optDouble("frequencyHz", Double.NaN).toFloat()
                if (!frequency.isFinite() || frequency <= 0f) return@mapNotNull null
                OpraEqBand(
                    type = type,
                    frequencyHz = frequency,
                    gainDb = band.optDouble("gainDb", 0.0).toFloat().takeIf { it.isFinite() } ?: 0f,
                    q = band.optDouble("q", Double.NaN).toFloat().takeIf { it.isFinite() },
                    slope = band.optDouble("slope", Double.NaN).toFloat().takeIf { it.isFinite() },
                )
            }
    }.getOrDefault(emptyList())
}

internal fun formatEqualizerFilters(filters: List<OpraEqBand>): String {
    val array = JSONArray()
    filters.forEach { band ->
        array.put(
            JSONObject().apply {
                put("type", band.type)
                put("frequencyHz", band.frequencyHz.toDouble())
                put("gainDb", band.gainDb.toDouble())
                band.q?.let { put("q", it.toDouble()) }
                band.slope?.let { put("slope", it.toDouble()) }
            },
        )
    }
    return array.toString()
}

internal fun EchoSavedPlaybackSession.toPreferenceValue(): String =
    JSONObject().apply {
        put("currentIndex", currentIndex)
        put("positionMs", positionMs.coerceAtLeast(0L))
        put("playWhenReady", playWhenReady)
        put("shuffleEnabled", shuffleEnabled)
        put("repeatMode", repeatMode.toPreferenceValue())
        put("playbackSpeed", playbackSpeed.toDouble())
        put("playbackPitch", playbackPitch.toDouble())
        put(
            "queue",
            JSONArray().apply {
                queue.forEach { track ->
                    put(
                        JSONObject().apply {
                            put("id", track.id)
                            put("uri", track.uri)
                            put("title", track.title)
                            put("artist", track.artist)
                            put("album", track.album)
                            put("artworkUri", track.artworkUri)
                            put("durationMs", track.durationMs.coerceAtLeast(0L))
                            track.sampleRateHz?.takeIf { it > 0 }?.let { put("sampleRateHz", it) }
                        },
                    )
                }
            },
        )
    }.toString()

internal data class EchoSavedPlaybackResume(
    val queueIdentity: String,
    val currentIndex: Int,
    val positionMs: Long,
    val playWhenReady: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: EchoRepeatMode,
    val playbackSpeed: Float,
    val playbackPitch: Float,
)

internal fun EchoSavedPlaybackSession.playbackQueueIdentity(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    queue.forEach { track ->
        listOf(
            track.id,
            track.uri,
            track.title,
            track.artist,
            track.album.orEmpty(),
            track.artworkUri.orEmpty(),
            track.durationMs.toString(),
            (track.sampleRateHz?.takeIf { it > 0 } ?: 0).toString(),
        ).forEach { value ->
            digest.update(value.toByteArray(StandardCharsets.UTF_8))
            digest.update(0.toByte())
        }
    }
    return digest.digest().joinToString(separator = "") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
}

internal fun EchoSavedPlaybackSession.toResumePreferenceValue(queueIdentity: String): String =
    JSONObject().apply {
        put("queueIdentity", queueIdentity)
        put("currentIndex", currentIndex)
        put("positionMs", positionMs.coerceAtLeast(0L))
        put("playWhenReady", playWhenReady)
        put("shuffleEnabled", shuffleEnabled)
        put("repeatMode", repeatMode.toPreferenceValue())
        put("playbackSpeed", playbackSpeed.toDouble())
        put("playbackPitch", playbackPitch.toDouble())
    }.toString()

internal fun parsePlaybackResume(raw: String): EchoSavedPlaybackResume? =
    runCatching {
        val json = JSONObject(raw)
        val queueIdentity = json.optString("queueIdentity").takeIf { it.isNotBlank() }
            ?: return@runCatching null
        EchoSavedPlaybackResume(
            queueIdentity = queueIdentity,
            currentIndex = json.optInt("currentIndex", -1),
            positionMs = json.optLong("positionMs").coerceAtLeast(0L),
            playWhenReady = json.optBoolean("playWhenReady", false),
            shuffleEnabled = json.optBoolean("shuffleEnabled", false),
            repeatMode = parseSavedRepeatMode(json.optString("repeatMode")),
            playbackSpeed = json.optDouble("playbackSpeed", 1.0).toFloat().coerceIn(0.5f, 2.0f),
            playbackPitch = json.optDouble("playbackPitch", 1.0).toFloat().coerceIn(0.5f, 2.0f),
        )
    }.getOrNull()

internal fun EchoSavedPlaybackSession.withPlaybackResume(
    resume: EchoSavedPlaybackResume?,
): EchoSavedPlaybackSession {
    if (resume == null || resume.queueIdentity != playbackQueueIdentity()) return this
    if (resume.currentIndex !in queue.indices) return this
    return copy(
        currentIndex = resume.currentIndex,
        positionMs = resume.positionMs,
        playWhenReady = resume.playWhenReady,
        shuffleEnabled = resume.shuffleEnabled,
        repeatMode = resume.repeatMode,
        playbackSpeed = resume.playbackSpeed,
        playbackPitch = resume.playbackPitch,
    )
}

internal fun parsePlaybackSession(raw: String): EchoSavedPlaybackSession? =
    runCatching {
        val json = JSONObject(raw)
        val queueJson = json.optJSONArray("queue") ?: return@runCatching null
        val queue = buildList {
            for (index in 0 until queueJson.length()) {
                val item = queueJson.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val uri = item.optString("uri").takeIf { it.isNotBlank() } ?: continue
                add(
                    EchoTrackRef(
                        id = id,
                        uri = uri,
                        title = item.optString("title").ifBlank { "Unknown Track" },
                        artist = item.optString("artist").ifBlank { "Unknown Artist" },
                        album = item.optString("album").takeIf { it.isNotBlank() },
                        artworkUri = item.optString("artworkUri").takeIf { it.isNotBlank() },
                        durationMs = item.optLong("durationMs").coerceAtLeast(0L),
                        sampleRateHz = item.optInt("sampleRateHz").takeIf { it > 0 },
                    ),
                )
            }
        }
        val currentIndex = json.optInt("currentIndex", -1)
        if (queue.isEmpty() || currentIndex !in queue.indices) return@runCatching null
        EchoSavedPlaybackSession(
            queue = queue,
            currentIndex = currentIndex,
            positionMs = json.optLong("positionMs").coerceAtLeast(0L),
            playWhenReady = json.optBoolean("playWhenReady", false),
            shuffleEnabled = json.optBoolean("shuffleEnabled", false),
            repeatMode = parseSavedRepeatMode(json.optString("repeatMode")),
            playbackSpeed = json.optDouble("playbackSpeed", 1.0).toFloat().coerceIn(0.5f, 2.0f),
            playbackPitch = json.optDouble("playbackPitch", 1.0).toFloat().coerceIn(0.5f, 2.0f),
        )
    }.getOrNull()

internal fun EchoRepeatMode.toPreferenceValue(): String = when (this) {
    EchoRepeatMode.All -> "all"
    EchoRepeatMode.One -> "one"
    EchoRepeatMode.Off -> "off"
}

internal fun parseSavedRepeatMode(raw: String?): EchoRepeatMode = when (raw?.lowercase()) {
    "all" -> EchoRepeatMode.All
    "one" -> EchoRepeatMode.One
    else -> EchoRepeatMode.Off
}
