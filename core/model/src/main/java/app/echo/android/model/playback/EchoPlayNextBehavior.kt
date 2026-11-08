package app.echo.android.model.playback

enum class EchoPlayNextBehavior(val id: String) {
    Repeat("repeat"), MoveUpcoming("move_upcoming");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Repeat
    }
}
