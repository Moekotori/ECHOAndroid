package app.echo.android.model.lyrics

data class EchoLyrics(
    val lines: List<EchoLyricLine> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val sourceLabel: String? = null,
    val format: EchoLyricsFormat = EchoLyricsFormat.Lrc,
    val offsetMs: Long = 0L,
) {
    val isSynced: Boolean
        get() = lines.any { it.startMs >= 0L }
}

enum class EchoLyricsFormat {
    Lrc,
    EnhancedLrc,
    Ttml,
    Srt,
    Vtt,
    Ass,
    Yrc,
    Qrc,
    Krc,
    PlainText,
}

data class EchoLyricLine(
    val startMs: Long,
    val endMs: Long? = null,
    val text: String,
    val translation: String? = null,
    val romanization: String? = null,
    val words: List<EchoLyricWord> = emptyList(),
    val speaker: String? = null,
    val isBackground: Boolean = false,
)

data class EchoLyricWord(
    val startMs: Long,
    val endMs: Long? = null,
    val text: String,
)

sealed interface EchoLyricsLoadState {
    data object Idle : EchoLyricsLoadState
    data object Loading : EchoLyricsLoadState
    data object Missing : EchoLyricsLoadState
    data class Ready(val lyrics: EchoLyrics) : EchoLyricsLoadState
    data class Error(val message: String) : EchoLyricsLoadState
}

/** A user-selectable provider result; contains the complete preview for offline selection. */
data class EchoLyricsCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val lyrics: EchoLyrics,
)
