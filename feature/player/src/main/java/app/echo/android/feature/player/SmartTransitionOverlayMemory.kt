package app.echo.android.feature.player

import app.echo.android.model.playback.EchoSmartTransitionVisual

internal data class SmartTransitionOverlayFrame(
    val visual: EchoSmartTransitionVisual,
    val currentArtwork: String?,
    val isPlaying: Boolean,
    val progress: Float,
)

/** At most one small snapshot for an exit; updated only from a committed Compose SideEffect. */
internal class SmartTransitionOverlayMemory {
    var frame: SmartTransitionOverlayFrame? = null
        private set
    fun retain(value: SmartTransitionOverlayFrame) { frame = value }
    fun clear() { frame = null }
}
