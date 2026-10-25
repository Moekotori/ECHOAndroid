package app.echo.android.model.settings

/** Stable IDs keep category preferences independent of UI labels and enum ordinals. */
enum class EchoLibraryCategory(val id: String) {
    Songs("songs"), Albums("albums"), Artists("artists"), Folders("folders"), Playlists("playlists");

    companion object {
        fun fromId(id: String): EchoLibraryCategory? = entries.firstOrNull { it.id == id }
    }
}

enum class EchoTrackMenuAction(val id: String) {
    EditTags("edit-tags"), ImportLyrics("import-lyrics"), Artwork("artwork"), Versions("versions"), Hide("hide"), Delete("delete");
    companion object { fun fromId(id: String) = entries.firstOrNull { it.id == id } }
}

data class EchoLibraryLayout(
    val order: List<EchoLibraryCategory> = EchoLibraryCategory.entries.toList(),
    val hidden: Set<EchoLibraryCategory> = emptySet(),
    val showFileNames: Boolean = false,
    val hiddenTrackActions: Set<EchoTrackMenuAction> = emptySet(),
) {
    fun normalized(): EchoLibraryLayout {
        val unique = order.distinct()
        val complete = unique + EchoLibraryCategory.entries.filterNot { it in unique }
        // Corrupt or old preferences must never leave the category bar empty.
        val safeHidden = if (complete.all { it in hidden }) hidden - complete.first() else hidden
        return copy(order = complete, hidden = safeHidden)
    }

    /** A source may support fewer categories; keep an accessible fallback for that source. */
    fun visibleCategories(available: List<EchoLibraryCategory> = EchoLibraryCategory.entries): List<EchoLibraryCategory> {
        val layout = normalized()
        val supported = layout.order.filter { it in available }
        return supported.filterNot { it in layout.hidden }.ifEmpty { supported.take(1) }
    }

    fun withVisibility(category: EchoLibraryCategory, visible: Boolean): EchoLibraryLayout {
        val layout = normalized()
        if (!visible && layout.visibleCategories() == listOf(category)) return layout
        return layout.copy(hidden = if (visible) layout.hidden - category else layout.hidden + category)
    }

    fun move(category: EchoLibraryCategory, offset: Int): EchoLibraryLayout {
        val layout = normalized()
        val from = layout.order.indexOf(category)
        val to = (from + offset.coerceIn(-1, 1)).coerceIn(layout.order.indices)
        if (from == to) return layout
        val reordered = layout.order.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        return layout.copy(order = reordered)
    }
}
