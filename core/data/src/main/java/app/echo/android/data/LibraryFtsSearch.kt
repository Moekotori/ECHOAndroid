package app.echo.android.data

private val SearchTokenRegex = Regex("[\\p{L}\\p{M}\\p{N}]+")

internal fun sanitizeFtsQuery(rawQuery: String): String? {
    val tokens = SearchTokenRegex.findAll(rawQuery.trim().lowercase())
        .flatMap { match -> match.value.toFtsTokens().asSequence() }
        .filter { it.isNotBlank() }
        .map { token -> "$token*" }
        .toList()

    return tokens.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
}

/** Whitespace separates terms; punctuation and wildcard characters remain literal text. */
internal fun librarySearchTerms(query: String): List<String> =
    query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank).distinct()

/** Contains candidates use indexed ASCII trigrams and CJK characters, followed by exact verification. */
internal fun substringFtsQuery(query: String): String? =
    (LibrarySubstringIndex.tokens(listOf(query)) + query.filter(Char::isCjk).map { "$it*" })
        .takeIf { it.isNotEmpty() }?.joinToString(" AND ")

internal fun ftsRankQuery(rawQuery: String): String =
    "%${rawQuery.trim().lowercase()}%"

internal fun LibraryTrackEntity.toFtsEntity(): LibraryTrackFtsEntity =
    LibraryTrackFtsEntity(
        trackId = id,
        title = title,
        artist = artist,
        album = album.orEmpty(),
        albumArtist = albumArtist.orEmpty(),
        normalizedText = buildFtsNormalizedText(
            title,
            artist,
            album.orEmpty(),
            albumArtist.orEmpty(),
            normalizedTitle.orEmpty(),
            normalizedArtist.orEmpty(),
            normalizedAlbum.orEmpty(),
            normalizedAlbumArtist.orEmpty(),
            pinyinTitle.orEmpty(),
            pinyinArtist.orEmpty(),
            pinyinAlbum.orEmpty(),
        ),
    )

private fun String.toFtsTokens(): List<String> =
    if (any(Char::isCjk)) {
        filter { it.isLetterOrDigit() }
            .map { it.lowercase() }
    } else {
        listOf(this)
    }

internal fun buildFtsNormalizedText(vararg parts: String): String {
    val joined = parts
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .lowercase()
    val cjkTokens = joined
        .asSequence()
        .filter(Char::isCjk)
        .map(Char::toString)
        .joinToString(" ")
    val pinyinParts = parts
        .asSequence()
        .flatMap { part ->
            SearchTokenRegex.findAll(part.lowercase())
                .map { it.value }
                .filter { token -> token.all { c -> c.isLetterOrDigit() && c.code < 128 } }
        }
        .toList()
    return listOf(joined, cjkTokens, *pinyinParts.toTypedArray(), LibrarySubstringIndex.tokens(parts.asIterable()).joinToString(" "))
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

private fun Char.isCjk(): Boolean =
    this in '\u3400'..'\u4DBF' ||
        this in '\u4E00'..'\u9FFF' ||
        this in '\uF900'..'\uFAFF'
