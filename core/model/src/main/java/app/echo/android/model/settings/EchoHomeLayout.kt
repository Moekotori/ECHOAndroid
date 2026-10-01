package app.echo.android.model.settings

/** Stable IDs keep home layouts independent of translated labels and available library data. */
enum class EchoHomeSection(val id: String) {
    Resume("resume-listening"),
    Recent("recent"),
    DailyAlbum("daily-album"),
    Recommended("recommended"),
    Favorites("favorites"),
    Rediscover("rediscover"),
    Artists("artists"),
    ListeningSummary("listening-summary"),
    Overview("overview"),
    ;

    companion object {
        fun fromId(id: String): EchoHomeSection? = entries.firstOrNull { it.id == id }
    }
}

data class EchoHomeLayout(
    val order: List<EchoHomeSection> = EchoHomeSection.entries.toList(),
    val hidden: Set<EchoHomeSection> = emptySet(),
) {
    /** Append newly introduced sections without changing the user's existing order or choices. */
    fun normalized(): EchoHomeLayout {
        val unique = order.distinct()
        return copy(order = unique + EchoHomeSection.entries.filterNot { it in unique })
    }

    fun withVisibility(section: EchoHomeSection, visible: Boolean): EchoHomeLayout =
        copy(hidden = if (visible) hidden - section else hidden + section)

    fun move(section: EchoHomeSection, offset: Int): EchoHomeLayout {
        val layout = normalized()
        val from = layout.order.indexOf(section)
        val to = (from + offset.coerceIn(-1, 1)).coerceIn(layout.order.indices)
        if (from == to) return layout
        val reordered = layout.order.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        return layout.copy(order = reordered)
    }
}
