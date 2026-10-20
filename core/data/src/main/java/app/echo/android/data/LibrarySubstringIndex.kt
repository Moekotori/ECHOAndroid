package app.echo.android.data

/** ASCII trigrams share the existing FTS row and are only candidates for the exact text predicate. */
internal object LibrarySubstringIndex {
    private const val Prefix = "eg3"

    fun tokens(parts: Iterable<String>): Set<String> = buildSet {
        parts.forEach { part ->
            var run = 0
            part.forEachIndexed { index, char ->
                val ascii = char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9'
                run = if (ascii) run + 1 else 0
                if (run >= 3) add(Prefix + part.substring(index - 2, index + 1).lowercase())
            }
        }
    }
}
