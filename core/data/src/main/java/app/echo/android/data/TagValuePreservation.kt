package app.echo.android.data

/** The editor changes the number, never the original total. Clearing removes both. */
internal fun tagNumberWithTotal(number: Int?, original: String?): String? = number?.let {
    val total = original?.substringAfter('/', "")?.trim()?.takeIf { value -> value.toIntOrNull()?.let { it > 0 } == true }
    if (total == null) it.toString() else "$it/$total"
}

/** Keep a full date/timestamp unless its year was explicitly changed or cleared. */
internal fun tagDateWithYear(year: Int?, original: String?): String? = year?.let {
    original?.takeIf { value -> value.take(4).toIntOrNull() == year } ?: it.toString()
}

/** Equal projected values mean the original multi-value representation was not edited. */
internal fun shouldReplaceOptionalTextTag(value: String?, modified: Boolean, original: String?): Boolean {
    if (value == null && !modified) return false
    val next = value.orEmpty().trim()
    if (next.isEmpty()) return true
    return next != original.orEmpty().trim()
}

/** Keep separate values/frames when the editor's display value has not changed. */
internal fun preserveTagNames(value: String?, original: String?): Boolean =
    !value.isNullOrBlank() && LibraryArtistPolicy.display(listOf(value)) ==
        LibraryArtistPolicy.display(listOf(original.orEmpty()))
