package app.echo.android.feature.library

import app.echo.android.model.library.*

class LibraryExperienceActions(
    val preview: suspend (EchoSmartPlaylistRule) -> EchoSmartPlaylistPreview,
    val rule: suspend (String) -> EchoSmartPlaylistRule?,
    val save: suspend (String?, String, EchoSmartPlaylistRule) -> EchoPlaylist,
    val pin: suspend (String, Boolean) -> Boolean,
    val inspect: suspend () -> List<EchoLibraryRepairItem>,
    val candidates: suspend (EchoTrack) -> List<EchoTrack>,
    val relink: suspend (String, EchoTrack) -> Unit,
    val chooseFile: suspend () -> EchoTrack?,
    val rescanFolder: () -> Unit = {},
)
