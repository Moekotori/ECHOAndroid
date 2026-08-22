package app.echo.android.data

/**
 * Legacy rows copied `pinyin* = lower(trim(...))` as a placeholder. The backfill
 * query treats that as unfinished whenever the field still contains non-ASCII.
 *
 * Chinese conversion produces distinct pinyin and leaves the query naturally.
 * Japanese, Korean, and accented Latin keep `pinyin == normalized`, so writing
 * them again would invalidate Room paging forever. Stamp those fields so they
 * no longer match the placeholder predicate.
 */
internal object LibraryPinyinBackfillPolicy {
    private const val COMPLETED_MARK = '\u200B'

    fun charNeedsPinyin(ch: Char): Boolean =
        ch in '\u3400'..'\u4DBF' ||
            ch in '\u4E00'..'\u9FFF' ||
            ch in '\uF900'..'\uFAFF'

    fun matchesBackfillQuery(track: LibraryTrackEntity): Boolean =
        fieldMatchesBackfillQuery(track.title, track.pinyinTitle, track.normalizedTitle) ||
            fieldMatchesBackfillQuery(track.artist, track.pinyinArtist, track.normalizedArtist) ||
            fieldMatchesBackfillQuery(track.album, track.pinyinAlbum, track.normalizedAlbum)

    fun apply(track: LibraryTrackEntity): LibraryTrackEntity {
        val computed = track.withComputedSearchMetadata()
        return computed.copy(
            pinyinTitle = finalizePinyinField(computed.title, computed.pinyinTitle, computed.normalizedTitle),
            pinyinArtist = finalizePinyinField(computed.artist, computed.pinyinArtist, computed.normalizedArtist),
            pinyinAlbum = finalizePinyinField(computed.album, computed.pinyinAlbum, computed.normalizedAlbum),
        )
    }

    private fun fieldMatchesBackfillQuery(
        source: String?,
        pinyin: String?,
        normalized: String?,
    ): Boolean =
        source.hasNonAscii() && (pinyin.isNullOrEmpty() || pinyin == normalized)

    private fun finalizePinyinField(
        source: String?,
        pinyin: String?,
        normalized: String?,
    ): String? {
        if (!source.hasNonAscii()) return pinyin
        val value = pinyin ?: normalized ?: return null
        return if (value.isEmpty() || value == normalized) {
            value + COMPLETED_MARK
        } else {
            value
        }
    }

    private fun String?.hasNonAscii(): Boolean =
        !isNullOrEmpty() && any { it !in ' '..'~' }
}
