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
