package app.echo.android.connect

object EchoSystemCastReturnPolicy {
    /** A foreign receiver item must never replace the phone queue or be fetched locally. */
    fun index(publishedUrls: Map<String, Int>, contentId: String?): Int? = contentId?.let(publishedUrls::get)

    fun sameQueue(expectedCount: Int, currentCount: Int, expectedAnchor: String?, currentAnchor: String?): Boolean =
        expectedCount > 0 && expectedCount == currentCount && !expectedAnchor.isNullOrBlank() && expectedAnchor == currentAnchor

    fun targetIndex(expectedEntry: String?, currentEntries: List<Pair<Int, String?>>): Int? =
        expectedEntry?.takeIf(String::isNotBlank)?.let { expected -> currentEntries.firstOrNull { it.second == expected }?.first }

    fun position(positionMs: Long, durationMs: Long): Long =
        positionMs.coerceAtLeast(0).let { if (durationMs > 0) it.coerceAtMost(durationMs) else it }
}
