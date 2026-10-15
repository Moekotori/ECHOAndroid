package app.echo.android.data

/** A null lyrics request preserves the existing tag; an empty request must actually clear it. */
internal fun AudioTagFields?.matchesWrittenTags(expected: AudioTagFields): Boolean =
    this != null && title == expected.title && artist == expected.artist &&
        (expected.lyrics == null || normalizedLyrics(lyrics) == normalizedLyrics(expected.lyrics))

private fun normalizedLyrics(value: String?): String =
    value.orEmpty().replace("\r\n", "\n").trimEnd('\u0000').trim()
