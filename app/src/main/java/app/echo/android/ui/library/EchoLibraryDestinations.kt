package app.echo.android.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.echo.android.EchoAndroidViewModel
import app.echo.android.design.EchoPageContent
import app.echo.android.design.LocalEchoPageInteractive
import app.echo.android.design.echoPageBackgroundColor
import app.echo.android.feature.library.MusicRelationsActions
import app.echo.android.feature.library.MusicRelationsPlaybackActions
import app.echo.android.feature.library.MusicRelationsScreen

/** Keep library state across its full-page tools, and keep outgoing content drawn until exit. */
@Composable
internal fun EchoLibraryDestinations(
    viewModel: EchoAndroidViewModel,
    interactive: Boolean,
    onOpenPlayer: () -> Unit,
    bottomInset: Dp,
    library: @Composable (openRelations: () -> Unit, openBroadcast: (String) -> Unit) -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf("library") }
    val pages = rememberSaveableStateHolder()
    val relations = remember(viewModel) {
        val source = viewModel.musicRelations
        MusicRelationsActions(source::initialize, source::entities, source::tracks, source::readFileTags, source::overview)
    }
    val relationPlayback = remember(viewModel) {
        MusicRelationsPlaybackActions(viewModel::playMusicRelationTracks,
            viewModel::playMusicRelationNext, viewModel::enqueueMusicRelation)
    }
    CompositionLocalProvider(LocalEchoPageInteractive provides (interactive && LocalEchoPageInteractive.current)) {
    EchoPageContent(
        targetState = destination,
        contentKey = { it },
        isBackward = { _, target -> target == "library" },
        modifier = Modifier.fillMaxSize().background(echoPageBackgroundColor()),
        label = "library-destinations",
    ) { page ->
        pages.SaveableStateProvider(page) {
            when (page) {
                "library" -> library({ destination = "relations" }, { destination = it })
                "relations" -> MusicRelationsScreen(relations,
                    onBack = { destination = "library" }, onPlay = viewModel::playMusicRelationTrack,
                    modifier = Modifier.padding(bottom = bottomInset), playback = relationPlayback)
                else -> EchoBroadcastHost(viewModel, page,
                    interactive = interactive && LocalEchoPageInteractive.current,
                    onBack = { destination = "library" }, onOpenPlayer = onOpenPlayer, bottomInset = bottomInset)
            }
        }
    }
    }
}
