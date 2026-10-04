package app.echo.android.feature.library

import androidx.paging.PagingData
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.PlaylistImportPreview
import app.echo.android.model.library.PlaylistTrackOrder
import app.echo.android.model.library.PlaylistStats
import kotlinx.coroutines.flow.Flow

class PlaylistManagementActions(
    val order: (String) -> Flow<PlaylistTrackOrder>,
    val stats: (String) -> Flow<PlaylistStats>,
    val sort: suspend (String, PlaylistTrackOrder) -> Unit,
    val candidates: (String) -> Flow<PagingData<EchoTrack>>,
    val add: suspend (String, List<String>) -> Int,
    val remove: suspend (String, String) -> Boolean,
    val move: suspend (String, String, Int) -> Boolean,
    val copy: suspend (String, String) -> EchoPlaylist?,
    val pickArtwork: (String) -> Unit,
)

enum class PlaylistImportFailure { Read, TooLarge, TooMany, Empty, NoMatches, Save, Export, Cover }

sealed interface PlaylistImportUiState {
    data object Reading : PlaylistImportUiState
    data class Preview(val name: String, val result: PlaylistImportPreview, val saving: Boolean = false) : PlaylistImportUiState
    data class Imported(val playlist: EchoPlaylist) : PlaylistImportUiState
    data object Exported : PlaylistImportUiState
    data class Failed(val reason: PlaylistImportFailure) : PlaylistImportUiState
}
