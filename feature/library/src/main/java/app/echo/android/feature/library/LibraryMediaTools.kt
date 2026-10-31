package app.echo.android.feature.library

import androidx.compose.runtime.*
import app.echo.android.model.library.*

data class LibraryMediaTools(
    val previewEncoding: suspend (EchoTrack, String?) -> EchoEncodingPreview,
    val applyEncoding: suspend (EchoTrack, EchoEncodingPreview) -> Unit,
    val restoreEncoding: suspend (EchoTrack) -> Unit,
    val images: suspend (EchoTrack, String?) -> List<EchoAlbumImage>,
    val previewFolder: suspend (EchoTrack, String) -> List<EchoFolderEncodingItem>,
    val applyFolder: suspend (List<EchoFolderEncodingItem>) -> EchoFolderEncodingResult,
)

internal val LocalLibraryMediaTools = staticCompositionLocalOf<LibraryMediaTools?> { null }
