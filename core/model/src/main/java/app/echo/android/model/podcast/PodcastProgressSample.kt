package app.echo.android.model.podcast

data class PodcastProgressSample(val id: String, val positionMs: Long, val ended: Boolean = false, val replay: Boolean = false)
