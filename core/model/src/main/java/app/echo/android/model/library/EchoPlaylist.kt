package app.echo.android.model.library

data class EchoPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val trackCount: Int = trackIds.size,
    val artworkUri: String? = null,
    val updatedAtEpochMs: Long = 0L,
    val source: String = LibrarySource.MediaStore.id,
) {
    val isLikedSongs: Boolean
        get() = id == LikedSongsId

    val smartKind: LibrarySmartPlaylistKind?
        get() = LibrarySmartPlaylistKind.fromId(id)

    val isSmartPlaylist: Boolean
        get() = smartKind != null

    val canEdit: Boolean
        get() = source == LibrarySource.MediaStore.id && !isLikedSongs && !isSmartPlaylist

    val canRemoveTracks: Boolean
        get() = canEdit || isLikedSongs

    companion object {
        const val LikedSongsId = "local:liked"
    }
}

enum class LibrarySmartPlaylistKind(val id: String) {
    Recent("local:recent"),
    Frequent("local:frequent"),
    Never("local:never"),
    Added("local:added"),
    ;

    companion object {
        fun fromId(value: String?): LibrarySmartPlaylistKind? {
            val id = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.id == id }
        }
    }
}
