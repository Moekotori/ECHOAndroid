package app.echo.android.model.error

enum class EchoErrorSource {
    Crash,
    Playback,
    Library,
    Connect,
    Network,
    Usb,
    Lyrics,
    Other,
    ;

    companion object {
        fun fromId(raw: String?): EchoErrorSource =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: Other
    }
}

data class EchoErrorProcessInfo(
    val appVersion: String,
)

data class EchoErrorRecord(
    val id: Long,
    val occurredAtEpochMs: Long,
    val source: EchoErrorSource,
    val summary: String,
    val detail: String?,
    val stackTrace: String?,
    val count: Int,
    val threadName: String? = null,
    val appVersion: String? = null,
    val throwableName: String? = null,
    val firstOccurredAtEpochMs: Long = occurredAtEpochMs,
) {
    fun matchesQuery(needle: String): Boolean {
        if (needle.isBlank()) return true
        return summary.contains(needle, ignoreCase = true) ||
            detail.orEmpty().contains(needle, ignoreCase = true) ||
            stackTrace.orEmpty().contains(needle, ignoreCase = true) ||
            source.name.contains(needle, ignoreCase = true) ||
            threadName.orEmpty().contains(needle, ignoreCase = true) ||
            appVersion.orEmpty().contains(needle, ignoreCase = true) ||
            throwableName.orEmpty().contains(needle, ignoreCase = true)
    }

    fun toDiagnosticText(): String = buildString {
        appendLine("source=${source.name}")
        appendLine("count=$count")
        appendLine("occurredAtEpochMs=$occurredAtEpochMs")
        appendLine("firstOccurredAtEpochMs=$firstOccurredAtEpochMs")
        appVersion?.let { appendLine("appVersion=$it") }
        threadName?.let { appendLine("thread=$it") }
        throwableName?.let { appendLine("throwable=$it") }
        appendLine("summary=$summary")
        detail?.let { appendLine("detail=$it") }
        stackTrace?.let { appendLine("stackTrace=$it") }
    }.trim()
}

data class EchoErrorDraft(
    val source: EchoErrorSource,
    val summary: String,
    val detail: String? = null,
    val stackTrace: String? = null,
    val occurredAtEpochMs: Long = System.currentTimeMillis(),
    val throwable: Throwable? = null,
    val threadName: String? = null,
    val appVersion: String? = null,
    val throwableName: String? = null,
    val firstOccurredAtEpochMs: Long = occurredAtEpochMs,
)
