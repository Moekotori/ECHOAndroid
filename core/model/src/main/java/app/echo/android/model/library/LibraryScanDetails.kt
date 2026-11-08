package app.echo.android.model.library

enum class LibraryScanSkipReason { Duration, Size, Format, Directory, Name, ReadFailure }
data class LibraryScanSkippedItem(val reason: LibraryScanSkipReason, val name: String, val path: String? = null)
