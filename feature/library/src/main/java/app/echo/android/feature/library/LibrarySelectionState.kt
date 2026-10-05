package app.echo.android.feature.library

/** A restored cloud source must have a valid category before the first composition. */
internal fun restoredLibraryViewMode(savedIndex: Int, cloudOnly: Boolean): LibraryViewMode {
    if (cloudOnly) return LibraryViewMode.Albums
    val saved = LibraryViewMode.entries.getOrNull(savedIndex) ?: LibraryViewMode.Songs
    return if (saved == LibraryViewMode.Cloud) LibraryViewMode.Songs else saved
}
