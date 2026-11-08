package app.echo.android.model.settings

data class LastFmOptions(
    val nowPlayingEnabled: Boolean = true,
    val scrobbleEnabled: Boolean = true,
    val loveSyncEnabled: Boolean = false,
)

data class LastFmActivity(
    val artist: String = "",
    val title: String = "",
    val playedSeconds: Long = 0,
    val thresholdSeconds: Long = 0,
    val scrobbled: Boolean = false,
    val lastNowPlayingAt: Long? = null,
    val lastScrobbleAt: Long? = null,
    val pendingCount: Int = 0,
)

enum class LastFmImportPhase { Idle, Running, Completed, Cancelled, Failed }

data class LastFmImportStatus(
    val phase: LastFmImportPhase = LastFmImportPhase.Idle,
    val processed: Int = 0,
    val lovedAdded: Int = 0,
    val playCountsUpdated: Int = 0,
    val unmatched: Int = 0,
    val ambiguous: Int = 0,
)

data class LastFmLoveSyncStatus(
    val scanning: Boolean = false,
    val synced: Int = 0,
    val pending: Int = 0,
    val failed: Boolean = false,
    val capacityReached: Boolean = false,
)
