package app.echo.android.data

internal const val UnknownAlbumKey = "未知专辑"
internal const val UnknownArtistKey = "未知艺术家"
internal const val UnknownTrackTitle = "未知曲目"
internal const val VariousArtistsKey = "various artists"

internal fun libraryAlbumKey(
    normalizedAlbum: String?,
    normalizedAlbumArtist: String?,
    normalizedArtist: String?,
): String =
    "${normalizedAlbum.normalizedKeyFallback(UnknownAlbumKey)}::" +
        canonicalAlbumArtistKey(normalizedAlbumArtist)
            .normalizedKeyFallback(normalizedArtist.normalizedKeyFallback(UnknownArtistKey))

internal fun libraryArtistKey(normalizedArtist: String?): String =
    normalizedArtist.normalizedKeyFallback(UnknownArtistKey)

internal fun libraryGenreKey(normalizedGenre: String?): String =
    normalizedGenre?.takeIf { it.isNotBlank() }.orEmpty()

internal fun canonicalUnknownArtist(): String = UnknownArtistKey

internal fun String?.takeUnlessUnknownMetadata(): String? {
    val trimmed = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return trimmed.takeUnless { LibraryMetadataSentinels.isUnknown(it) }
}

internal object LibraryMetadataSentinels {
    fun isUnknown(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized in UnknownValues
    }

    fun isVariousArtists(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized in VariousArtistValues
    }
}

private fun canonicalAlbumArtistKey(normalizedAlbumArtist: String?): String? {
    val value = normalizedAlbumArtist?.takeIf { it.isNotBlank() } ?: return null
    return if (LibraryMetadataSentinels.isVariousArtists(value)) VariousArtistsKey else value
}

private fun String?.normalizedKeyFallback(fallback: String): String =
    this?.takeIf { it.isNotBlank() } ?: fallback

private val UnknownValues = setOf(
    "<unknown>",
    "unknown artist",
    "unknown album",
    "unknown track",
    "unknown title",
    "未知艺术家",
    "未知专辑",
    "未知曲目",
    "不明なアーティスト",
    "不明なアルバム",
    "不明な曲",
)

private val VariousArtistValues = setOf(
    "various artists",
    "various artist",
    "群星",
    "合辑",
    "オムニバス",
)
