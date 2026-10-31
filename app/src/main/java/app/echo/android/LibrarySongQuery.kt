package app.echo.android

import app.echo.android.model.library.LibraryTrackFilter
import app.echo.android.model.library.LibraryTrackSortMode

/** Capture at the UI action boundary, before a playback command or I/O dispatcher can defer work. */
internal data class LibrarySongQuery(
    val text: String = "",
    val sort: LibraryTrackSortMode = LibraryTrackSortMode.Title,
    val filter: LibraryTrackFilter = LibraryTrackFilter(),
)
