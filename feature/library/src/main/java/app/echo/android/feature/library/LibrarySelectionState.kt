package app.echo.android.feature.library

import app.echo.android.model.settings.EchoLibraryLayout

/** Resolve restored and hidden categories before composing tabs or the pager. */
internal fun restoredLibraryViewMode(
    savedIndex: Int,
    cloudOnly: Boolean,
    layout: EchoLibraryLayout = EchoLibraryLayout(),
): LibraryViewMode {
    if (cloudOnly) return LibraryViewMode.Albums
    val modes = libraryPagerModes(false, layout)
    val saved = LibraryViewMode.entries.getOrNull(savedIndex)
    return if (saved == LibraryViewMode.Radio || saved in modes) saved!! else modes.first()
}
