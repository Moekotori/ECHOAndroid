package app.echo.android.model.backup

import app.echo.android.model.playback.EchoChannelBalanceState
import app.echo.android.model.playback.EchoTrackTransitionOptions
import app.echo.android.model.playback.OpraEqBand

data class EchoBackupDocument(
    val version: Int = CurrentVersion,
    val exportedAtEpochMs: Long = 0L,
    val settings: EchoBackupSettings = EchoBackupSettings(),
    val playlists: List<EchoBackupPlaylist> = emptyList(),
    val favorites: List<EchoBackupTrackRef> = emptyList(),
) {
    companion object {
        const val CurrentVersion = 1
    }
}

data class EchoBackupSettings(
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
    val usbExclusiveEnabled: Boolean? = null,
    val usbBitPerfectEnabled: Boolean? = null,
    val usbExclusiveAutoRequestOnStartup: Boolean? = null,
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
    val channelBalance: EchoChannelBalanceState? = null,
    val lyricsFontFamily: String? = null,
    val lyricsFontScale: Float? = null,
    val lyricsColorMode: String? = null,
    val lyricsAlignment: String? = null,
    val lyricsLineSpacing: Float? = null,
    val lyricsBackgroundDim: Float? = null,
    val lyricsWordHighlightEnabled: Boolean? = null,
    val lyricsWordHighlightIntensity: Float? = null,
    val lyricsImmersiveModeEnabled: Boolean? = null,
    val lyricsMotionMode: String? = null,
    val lyricsShowTranslation: Boolean? = null,
    val lyricsShowRomanization: Boolean? = null,
    val lyricsFocusGlowEnabled: Boolean? = null,
    val uiFontFamily: String? = null,
    val uiFontScale: Float? = null,
    val uiDensityScale: Float? = null,
)

data class EchoBackupPlaylist(
    val name: String,
    val tracks: List<EchoBackupTrackRef> = emptyList(),
)

data class EchoBackupTrackRef(
    val title: String = "",
    val artist: String = "",
    val relativePath: String? = null,
    val durationMs: Long = 0L,
)

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
