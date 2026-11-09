package app.echo.android.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.design.LocalEchoPageInteractive
import app.echo.android.feature.library.PlaylistImportDialog
import app.echo.android.model.library.EchoPlaylist

@Composable
internal fun rememberPlaylistImport(
    viewModel: EchoAndroidViewModel,
    active: Boolean = true,
    onOpen: ((EchoPlaylist) -> Unit)? = null,
): () -> Unit {
    val tools = viewModel.playlistTools
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(tools::prepare)
    }
    if (active && LocalEchoPageInteractive.current) {
        val state by tools.importState.collectAsStateWithLifecycle()
        PlaylistImportDialog(
            state = state,
            onConfirm = tools::confirm,
            onDismiss = tools::dismiss,
            onOpen = onOpen,
            searchCandidates = tools::importCandidates,
            onCorrect = tools::correctImport,
        )
    }
    return { picker.launch(arrayOf("*/*")) }
}
