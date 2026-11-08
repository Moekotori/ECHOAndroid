package app.echo.android.data

import java.text.Normalizer
import java.util.Locale

internal fun lastFmMatchKey(title: String, artist: String): String =
    lastFmMatchText(title) + "\u0000" + lastFmMatchText(artist)

private val lastFmWhitespace = Regex("\\s+")
internal fun lastFmMatchText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .trim().replace(lastFmWhitespace, " ").lowercase(Locale.ROOT)

