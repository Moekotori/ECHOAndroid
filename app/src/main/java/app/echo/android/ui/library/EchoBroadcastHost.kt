package app.echo.android.ui.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.EchoApplication
import app.echo.android.feature.podcasts.*
import app.echo.android.model.radio.EchoRadioStation
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Surface

@Composable
internal fun EchoBroadcastHost(viewModel: EchoAndroidViewModel, page: String, interactive: Boolean, onBack: () -> Unit, onOpenPlayer: () -> Unit, bottomInset: Dp) {
    val context = LocalContext.current
    val metadata by viewModel.playbackMetadata.collectAsStateWithLifecycle()
    val controls by viewModel.playbackControls.collectAsStateWithLifecycle()
    val openUrl: (String) -> Unit = { url ->
        if (EchoRadioStation.validUrl(url)) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    Surface(Modifier.fillMaxSize().padding(bottom = bottomInset), color = app.echo.android.design.echoPageBackgroundColor()) {
    Box(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
    if (page == "podcasts") {
        val controller = viewModel.podcasts
        val state by controller.state.collectAsStateWithLifecycle()
        LifecycleStartEffect(controller, interactive) {
            if (interactive) controller.open()
            onStopOrDispose { controller.close() }
        }
        BackHandler(enabled = interactive) { if (state.selected != null) controller.select(null) else onBack() }
        val actions = remember(controller, viewModel, context) {
            PodcastActions(controller::select, controller::searchFeeds, controller::searchEpisodes,
                controller::filter, controller::page, controller::subscribe, controller::refresh,
                controller::unfollow, controller::retry,
                listen = { controller.state.value.selected?.let(viewModel.broadcastPlayback::listenPodcast) },
                play = { episode, restart -> controller.state.value.selected?.let { viewModel.broadcastPlayback.playPodcast(it, episode, restart) } },
                enqueue = { episode, next -> controller.state.value.selected?.let { viewModel.broadcastPlayback.enqueuePodcast(it, episode, next) } },
                markPlayed = controller::markPlayed, openUrl = openUrl)
        }
        PodcastScreen(state, actions, onBack, metadata.mediaId, controls.isPlaying,
            playbackPending = interactive && controls.state in setOf(app.echo.android.model.playback.EchoPlaybackState.Loading, app.echo.android.model.playback.EchoPlaybackState.Buffering),
            playerStrip = { if (interactive) PodcastListeningStrip(viewModel) })
    } else {
        EchoRadioHost(viewModel, metadata.mediaId, metadata.title, controls, onBack, openUrl, onOpenPlayer, interactive)
    }
    }
    }
}
