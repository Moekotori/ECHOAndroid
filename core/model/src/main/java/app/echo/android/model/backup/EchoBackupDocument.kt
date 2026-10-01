package app.echo.android.model.backup

import app.echo.android.model.playback.EchoChannelBalanceState
import app.echo.android.model.settings.EchoSavedColorTheme
import app.echo.android.model.playback.EchoEqualizerUserPreset
import app.echo.android.model.playback.EchoTrackTransitionOptions
import app.echo.android.model.playback.OpraEqBand

data class EchoBackupDocument(
    val version: Int = CurrentVersion,
    val exportedAtEpochMs: Long = 0L,
    val settings: EchoBackupSettings = EchoBackupSettings(),
    val playlists: List<EchoBackupPlaylist> = emptyList(),
    val favorites: List<EchoBackupTrackRef> = emptyList(),
    val bookmarks: List<EchoBackupBookmark> = emptyList(),
    val history: List<EchoBackupHistoryEvent> = emptyList(),
    val lyrics: List<EchoBackupLyrics> = emptyList(),
    val assets: List<EchoBackupAsset> = emptyList(),
) {
    companion object {
        const val CurrentVersion = 3
    }
}

data class EchoBackupSettings(
    val homeLayout: app.echo.android.model.settings.EchoHomeLayout? = null,
    val playerPageStyle: String? = null,
    val playerTextScale: Float? = null,
    val playerArtworkScale: Float? = null,
    val backgroundMode: String? = null,
    val backgroundStyle: String? = null,
    val backgroundBlur: Float? = null,
    val backgroundBrightness: Float? = null,
    val backgroundGlass: Float? = null,
    val backgroundScale: Float? = null,
    val themeMode: String? = null,
    val colorTheme: String? = null,
    val appLanguage: String? = null,
    val performanceMode: String? = null,
    val dynamicColorEnabled: Boolean? = null,
    val dynamicArtworkEnabled: Boolean? = null,
    val compactModeEnabled: Boolean? = null,
    val scheduledDarkModeEnabled: Boolean? = null,
    val scheduledDarkStartMinute: Int? = null,
    val scheduledDarkEndMinute: Int? = null,
    val playbackHapticsEnabled: Boolean? = null,
    val pcHandoffEnabled: Boolean? = null,
    val showLyricsControlDeck: Boolean? = null,
    val onlineLyricsEnabled: Boolean? = null,
    val lockScreenLyricsEnabled: Boolean? = null,
    val usbExclusiveEnabled: Boolean? = null,
    val usbBitPerfectEnabled: Boolean? = null,
    val usbExclusiveAutoRequestOnStartup: Boolean? = null,
    val pauseOnAudioDisconnect: Boolean? = null,
    val resumeOnAudioReconnect: Boolean? = null,
    val trackAudioInfoTagsVisible: Boolean? = null,
    val replayGainEnabled: Boolean? = null,
    val replayGainMode: String? = null,
    val replayGainPreampDb: Float? = null,
    val trackTransitions: EchoTrackTransitionOptions? = null,
    val equalizerEnabled: Boolean? = null,
    val equalizerPreset: String? = null,
    val equalizerBandGains: List<Float>? = null,
    val equalizerPreampDb: Float? = null,
    val equalizerParametric: Boolean? = null,
    val equalizerSourceLabel: String? = null,
    val equalizerFilters: List<OpraEqBand>? = null,
    val equalizerUserPresets: List<EchoEqualizerUserPreset>? = null,
    val equalizerActiveUserPresetId: String? = null,
    val equalizerDevicePresetIds: Map<String, String>? = null,
    val opraLastQuery: String? = null,
    val channelBalance: EchoChannelBalanceState? = null,
    val lyricsPageStyle: String? = null,
    val lyricsFontFamily: String? = null,
    val lyricsFontScale: Float? = null,
    val lyricsColorMode: String? = null,
    val lyricsAlignment: String? = null,
    val lyricsLineSpacing: Float? = null,
    val lyricsBackgroundDim: Float? = null,
    val lyricsWordHighlightEnabled: Boolean? = null,
    val lyricsEstimatedWordHighlightEnabled: Boolean? = null,
    val lyricsWordHighlightIntensity: Float? = null,
    val lyricsImmersiveModeEnabled: Boolean? = null,
    val lyricsMotionMode: String? = null,
    val lyricsShowTranslation: Boolean? = null,
    val lyricsShowRomanization: Boolean? = null,
    val lyricsFocusGlowEnabled: Boolean? = null,
    val uiFontFamily: String? = null,
    val uiFontScale: Float? = null,
    val uiDensityScale: Float? = null,
    val customAccent: Int? = null,
    val customSecondary: Int? = null,
    val customBackground: Int? = null,
    val savedColorThemes: List<EchoSavedColorTheme>? = null,
    val appliedSavedColorThemeId: String? = null,
)

data class EchoBackupPlaylist(
    val name: String,
    val tracks: List<EchoBackupTrackRef> = emptyList(),
    val smartRule: app.echo.android.model.library.EchoSmartPlaylistRule? = null,
    val pinnedToHome: Boolean = false,
)

data class EchoBackupBookmark(val track: EchoBackupTrackRef, val positionMs: Long, val label: String)

data class EchoBackupTrackRef(
    val title: String = "",
    val artist: String = "",
    val relativePath: String? = null,
    val durationMs: Long = 0L,
    val album: String? = null,
)

data class EchoBackupHistoryEvent(val track: EchoBackupTrackRef, val listenedMs: Long, val playedAtEpochMs: Long,
    val localEpochDay: Long, val localHour: Int, val source: String?)
data class EchoBackupLyrics(val track: EchoBackupTrackRef, val documentJson: String?, val userOffsetMs: Long)
data class EchoBackupAsset(val role: String, val entry: String)
data class EchoBackupPreview(val currentPlaylists: Int, val incomingPlaylists: Int, val incomingFavorites: Int,
    val incomingMoments: Int, val incomingHistory: Int, val incomingLyrics: Int, val incomingAssets: Int,
    val matchedTracks: Int, val missingTracks: Int, val changedSettings: List<String>)

data class EchoBackupRestoreResult(
    val playlistsRestored: Int = 0,
    val favoritesRestored: Int = 0,
    val tracksMatched: Int = 0,
    val tracksMissing: Int = 0,
)

class EchoBackupException(message: String) : IllegalArgumentException(message)

sealed interface EchoBackupNotice {
    data object Exported : EchoBackupNotice
    data class Restored(val result: EchoBackupRestoreResult) : EchoBackupNotice
    data class Failed(val message: String) : EchoBackupNotice
}
