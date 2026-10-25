package app.echo.android.model.library

data class EchoHiddenTrack(val trackId: String, val title: String, val artist: String,
    val album: String?, val hiddenAtEpochMs: Long)
