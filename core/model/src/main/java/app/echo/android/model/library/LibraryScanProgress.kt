package app.echo.android.model.library

data class LibraryScanProgress(
    val phase: LibraryScanPhase = LibraryScanPhase.Idle,
    val scannedCount: Int = 0,
    val insertedCount: Int = 0,
    val updatedCount: Int = 0,
    val deletedCount: Int = 0,
    val totalCount: Int? = null,
    val currentTitle: String? = null,
    val error: String? = null,
    val isCompleted: Boolean = false,
) {
    val isScanning: Boolean
        get() = when (phase) {
            LibraryScanPhase.Preparing,
            LibraryScanPhase.QueryingMediaStore,
            LibraryScanPhase.Diffing,
            LibraryScanPhase.WritingDatabase,
            LibraryScanPhase.CleaningRemoved
            -> true
            LibraryScanPhase.Idle,
            LibraryScanPhase.Completed,
            LibraryScanPhase.Cancelled,
            LibraryScanPhase.Error
            -> false
        }
}

enum class LibraryScanPhase {
    Idle,
    Preparing,
    QueryingMediaStore,
    Diffing,
    WritingDatabase,
    CleaningRemoved,
    Completed,
    Cancelled,
    Error,
}
