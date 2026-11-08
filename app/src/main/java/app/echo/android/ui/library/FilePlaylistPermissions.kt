package app.echo.android.ui.library

/** Reserve headroom for the app's other document features; a folder needs just one grant. */
internal fun requireFilePlaylistGrantBudget(persisted: Set<String>, requested: List<String>) {
    val additions = requested.distinct().count { it !in persisted }
    require(additions <= (96 - persisted.size).coerceAtLeast(0)) {
        "Too many individual file permissions; select a folder instead"
    }
}
