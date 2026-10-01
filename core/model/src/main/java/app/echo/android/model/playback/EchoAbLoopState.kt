package app.echo.android.model.playback

data class EchoAbLoopState(val trackId: String? = null, val startMs: Long = 0, val endMs: Long = 0) {
    val active: Boolean get() = trackId != null && endMs - startMs >= 500
}
