package app.echo.android.data

import app.echo.android.model.library.ArtistSummary

/**
 * Resolves a display name to the same artist key the library index uses.
 * Unknown and various-artists labels are not artist pages.
 */
fun artistNavigationTarget(name: String, artworkUri: String? = null): ArtistSummary? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    if (LibraryMetadataSentinels.isUnknown(trimmed)) return null
    if (LibraryMetadataSentinels.isVariousArtists(trimmed)) return null
    return ArtistSummary(
        artistKey = libraryArtistKey(trimmed.normalizedForSearch()),
        name = trimmed,
        artworkUri = artworkUri,
        albumCount = 0,
        trackCount = 0,
        durationMs = 0L,
    )
}
