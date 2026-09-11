package app.echo.android.model.playback

enum class EchoReplayGainMode(val id: String) {
    Auto("auto"),
    Track("track"),
    Album("album"),
    ;

    companion object {
        fun fromId(value: String?): EchoReplayGainMode =
            entries.firstOrNull { it.id == value } ?: Auto
    }
}

data class EchoReplayGainTags(
    val trackGainDb: Float? = null,
    val albumGainDb: Float? = null,
) {
    fun selectedGainDb(mode: EchoReplayGainMode): Float? =
        when (mode) {
            EchoReplayGainMode.Track -> trackGainDb
            EchoReplayGainMode.Album -> albumGainDb ?: trackGainDb
            EchoReplayGainMode.Auto -> trackGainDb ?: albumGainDb
        }

    fun orElse(fallback: EchoReplayGainTags) = EchoReplayGainTags(
        trackGainDb = trackGainDb ?: fallback.trackGainDb,
        albumGainDb = albumGainDb ?: fallback.albumGainDb,
    )
}

const val EchoReplayGainPreampMinDb = -12f
const val EchoReplayGainPreampMaxDb = 6f

fun normalizeReplayGainPreampDb(value: Float): Float =
    value.coerceIn(EchoReplayGainPreampMinDb, EchoReplayGainPreampMaxDb)

sealed class EchoReplayGainScanState {
    data object Idle : EchoReplayGainScanState()
    data object Scanning : EchoReplayGainScanState()
    data class Written(val gainDb: Float) : EchoReplayGainScanState()
    data class Failed(val reason: EchoReplayGainScanFailure) : EchoReplayGainScanState()
}

enum class EchoReplayGainScanFailure {
    NotLocal,
    Unsupported,
    DecodeFailed,
    WriteFailed,
}
