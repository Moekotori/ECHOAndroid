package app.echo.android.ui.library

import androidx.compose.runtime.*
import app.echo.android.EchoAndroidViewModel
import app.echo.android.feature.library.LibraryMediaTools

@Composable
internal fun rememberLibraryMediaTools(viewModel: EchoAndroidViewModel): LibraryMediaTools = remember(viewModel) {
    val tools = viewModel.mediaInspection
    LibraryMediaTools(
        previewEncoding = { track, charset -> tools.encoding.preview(track.id, charset) },
        applyEncoding = { track, value -> tools.encoding.apply(track.id, value) },
        restoreEncoding = { track -> tools.encoding.restore(track.id) },
        images = tools.images::load,
        previewFolder = { track, charset -> tools.encoding.previewFolder(track.id, charset) },
        applyFolder = tools.encoding::applyFolder,
    )
}
