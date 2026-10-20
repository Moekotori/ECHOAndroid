package app.echo.android.model.connect

/** Physical source bounds; player positions remain relative to this segment. */
data class EchoRemoteSourceRange(val startSeconds: Double, val endSeconds: Double? = null) {
    init {
        require(startSeconds.isFinite() && startSeconds >= 0)
        require(endSeconds == null || (endSeconds.isFinite() && endSeconds > startSeconds))
    }
    val startMs: Long get() = kotlin.math.round(startSeconds * 1000).toLong()
    val endMs: Long get() = endSeconds?.let { kotlin.math.round(it * 1000).toLong() } ?: 0L
}
