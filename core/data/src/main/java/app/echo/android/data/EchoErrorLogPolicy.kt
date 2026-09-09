package app.echo.android.data

import app.echo.android.model.error.EchoErrorDraft
import app.echo.android.model.error.EchoErrorRecord
import java.io.PrintWriter
import java.io.StringWriter

object EchoErrorLogPolicy {
    const val MaxRecords = 500
    const val MergeWindowMs = 60_000L
    const val MaxSummaryChars = 400
    const val MaxDetailChars = 4_000
    const val MaxStackChars = 8_000
    const val MaxThreadChars = 80
    const val MaxAppVersionChars = 80
    const val MaxThrowableChars = 200
    const val PendingFileName = "echo-error-pending.json"

    fun normalizeSummary(raw: String): String =
        clip(sanitize(raw.trim()), MaxSummaryChars)

    fun normalizeDetail(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return clip(sanitize(text), MaxDetailChars)
    }

    fun normalizeStack(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return clip(sanitize(text), MaxStackChars)
    }

    fun stackTrace(throwable: Throwable?): String? {
        if (throwable == null) return null
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return normalizeStack(writer.toString())
    }

    fun shouldMerge(latest: EchoErrorRecord?, incoming: EchoErrorDraft): Boolean {
        if (latest == null) return false
        if (latest.source != incoming.source) return false
        if (latest.summary != incoming.summary) return false
        return incoming.occurredAtEpochMs - latest.occurredAtEpochMs in 0..MergeWindowMs
    }

    fun mergedFirstOccurredAt(latest: EchoErrorRecord, incoming: EchoErrorDraft): Long {
        val previous = latest.firstOccurredAtEpochMs.takeIf { it > 0L } ?: latest.occurredAtEpochMs
        return minOf(previous, incoming.occurredAtEpochMs)
    }

    fun normalizeThreadName(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { clip(it, MaxThreadChars) }

    fun normalizeAppVersion(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { clip(it, MaxAppVersionChars) }

    fun normalizeThrowableName(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { clip(it, MaxThrowableChars) }

    fun sanitize(raw: String): String =
        raw.replace(authorizationHeaderRegex, "$1<redacted>")
            .replace(urlUserInfoRegex, "$1<redacted>@")
            .replace(sensitiveQueryParameterRegex, "$1<redacted>")

    fun clip(raw: String, maxChars: Int): String {
        if (maxChars <= 0 || raw.length <= maxChars) return raw
        if (maxChars <= 3) return raw.take(maxChars)
        return raw.take(maxChars - 1) + "…"
    }

    private val authorizationHeaderRegex = Regex(
        pattern = """(?i)(\bauthorization\s*[:=]\s*)(?:basic|bearer)\s+[^\s,;]+""",
    )
    private val urlUserInfoRegex = Regex(
        pattern = """([a-zA-Z][a-zA-Z0-9+.-]*://)([^/@\s]+)@""",
    )
    private val sensitiveQueryParameterRegex = Regex(
        pattern = """(?i)([?&](?:access_token|api_key|apikey|auth|authorization|key|p|pass|passwd|password|pwd|s|salt|t|token|u|user|username)=)[^&#\s]+""",
    )
}
