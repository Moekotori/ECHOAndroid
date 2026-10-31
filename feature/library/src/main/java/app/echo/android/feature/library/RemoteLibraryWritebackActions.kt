package app.echo.android.feature.library

import androidx.paging.PagingData
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.flow.Flow

/** Available only for the configured source that supports Subsonic mutations. */
class RemoteLibraryWritebackActions(
    val sourceId: String,
    val playlists: Flow<List<EchoPlaylist>>,
    val tracks: (String) -> Flow<PagingData<EchoTrack>>,
    val candidates: (String) -> Flow<PagingData<EchoTrack>>,
    val create: suspend (String) -> Unit,
    val rename: suspend (String, String) -> Unit,
    val delete: suspend (String) -> Unit,
    val add: suspend (String, String) -> Unit,
    val remove: suspend (String, String) -> Unit,
    val navidrome: NavidromeActions? = null,
)
