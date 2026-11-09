package app.echo.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.echo.android.design.EchoPageOverlay
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate

internal val LocalTrackMetadataEditorLauncher =
    staticCompositionLocalOf<((EchoTrack, Boolean) -> Unit)?> { null }

class TrackMetadataEditorNavigation internal constructor() {
    internal data class Target(val track: EchoTrack, val canPickArtwork: Boolean)

    internal var target by mutableStateOf<Target?>(null)
        private set

    var isOpen by mutableStateOf(false)
        private set

    fun open(track: EchoTrack, canPickArtwork: Boolean) {
        target = Target(track, canPickArtwork)
        isOpen = true
    }

    internal fun close() {
        isOpen = false
    }

    internal fun clearAfterExit() {
        if (!isOpen) target = null
    }
}

@Composable
fun rememberTrackMetadataEditorNavigation(): TrackMetadataEditorNavigation =
    retain { TrackMetadataEditorNavigation() }

@Composable
fun TrackMetadataEditorLauncherProvider(
    onOpen: (EchoTrack, Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTrackMetadataEditorLauncher provides onOpen, content = content)
}

@Composable
fun TrackMetadataEditorOverlay(
    navigation: TrackMetadataEditorNavigation,
    loadLyrics: suspend (String) -> String?,
    onSave: suspend (EchoTrackMetadataUpdate) -> Boolean,
) {
    EchoPageOverlay(visible = navigation.isOpen, onHidden = navigation::clearAfterExit) {
        navigation.target?.let { target ->
            key(target.track.id) {
                TrackMetadataEditorScreen(
                    track = target.track,
                    loadLyrics = loadLyrics,
                    onSave = onSave,
                    onClose = navigation::close,
                    canPickArtwork = target.canPickArtwork,
                )
            }
        }
    }
}
