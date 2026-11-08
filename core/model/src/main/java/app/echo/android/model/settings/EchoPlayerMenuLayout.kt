package app.echo.android.model.settings

enum class EchoPlayerMenuAction(val id: String) {
    Appearance("appearance"), AddToPlaylist("playlist"), Share("share"), Equalizer("equalizer"),
    Album("album"), Info("info"), CopyInfo("copy"), OnlineAssets("online-assets"), Folder("folder"),
    Queue("queue"), Hide("hide"), Delete("delete"), Repeat("repeat"), Shuffle("shuffle"),
    SleepTimer("sleep-timer"), MoreSettings("more-settings");

    companion object {
        fun fromId(id: String): EchoPlayerMenuAction? = entries.firstOrNull { it.id == id }
    }
}

/** An omitted/new action gets its default position at the end; hiding never loses its position. */
data class EchoPlayerMenuLayout(
    val order: List<EchoPlayerMenuAction> = EchoPlayerMenuAction.entries.toList(),
    val hidden: Set<EchoPlayerMenuAction> = emptySet(),
) {
    fun normalized(): EchoPlayerMenuLayout = copy(order = order.distinct() + EchoPlayerMenuAction.entries.filter { it !in order })

    fun moved(action: EchoPlayerMenuAction, delta: Int): EchoPlayerMenuLayout {
        val layout = normalized()
        val index = layout.order.indexOf(action)
        val target = (index + delta).coerceIn(0, layout.order.lastIndex)
        if (target == index) return layout
        return layout.copy(order = layout.order.toMutableList().apply { removeAt(index); add(target, action) })
    }
}
