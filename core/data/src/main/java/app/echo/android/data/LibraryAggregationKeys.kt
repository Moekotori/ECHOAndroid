package app.echo.android.data

internal const val UnknownAlbumKey = "未知专辑"
internal const val UnknownArtistKey = "未知艺术家"

internal fun libraryAlbumKey(
    normalizedAlbum: String?,
    normalizedAlbumArtist: String?,
    normalizedArtist: String?,
): String =
    "${normalizedAlbum.normalizedKeyFallback(UnknownAlbumKey)}::" +
        normalizedAlbumArtist.normalizedKeyFallback(normalizedArtist.normalizedKeyFallback(UnknownArtistKey))

internal fun libraryArtistKey(normalizedArtist: String?): String =
    normalizedArtist.normalizedKeyFallback(UnknownArtistKey)

private fun String?.normalizedKeyFallback(fallback: String): String =
    this?.takeIf { it.isNotBlank() } ?: fallback
