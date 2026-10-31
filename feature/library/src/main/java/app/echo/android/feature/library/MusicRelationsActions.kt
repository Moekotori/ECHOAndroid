package app.echo.android.feature.library

import androidx.paging.PagingData
import app.echo.android.model.library.EchoMusicRelation
import app.echo.android.model.library.EchoMusicRelationKind
import app.echo.android.model.library.EchoMusicRelationSummary
import app.echo.android.model.library.EchoMusicRelationOverview
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class MusicRelationsActions(
    val initialize: suspend () -> Unit,
    val entities: (EchoMusicRelationKind, List<EchoMusicRelation>, String) -> Flow<PagingData<EchoMusicRelationSummary>>,
    val tracks: (List<EchoMusicRelation>, String) -> Flow<PagingData<EchoTrack>>,
    val readTags: suspend ((Int, Int) -> Unit) -> Unit,
    val overview: (List<EchoMusicRelation>) -> Flow<EchoMusicRelationOverview> = { flowOf(EchoMusicRelationOverview()) },
)

class MusicRelationsPlaybackActions(
    val playLoaded: (List<EchoTrack>) -> Unit,
    val playNext: (EchoTrack) -> Unit,
    val enqueue: (EchoTrack) -> Unit,
)
