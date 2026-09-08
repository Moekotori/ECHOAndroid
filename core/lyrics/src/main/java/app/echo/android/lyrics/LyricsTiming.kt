package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyrics

/** Always apply to the original lyrics, never to an already shifted copy. */
fun EchoLyrics.withUserOffset(offset: Long): EchoLyrics = copy(
    lines = lines.map { line ->
        if (line.startMs < 0L) line else line.copy(
            startMs = (line.startMs + offset).coerceAtLeast(0),
            endMs = line.endMs?.let { (it + offset).coerceAtLeast(0) },
            words = line.words.map { word -> word.copy(
                startMs = (word.startMs + offset).coerceAtLeast(0),
                endMs = word.endMs?.let { (it + offset).coerceAtLeast(0) },
            ) },
        )
    },
    offsetMs = offsetMs + offset,
    metadata = metadata + ("user_offset_ms" to offset.toString()),
)
