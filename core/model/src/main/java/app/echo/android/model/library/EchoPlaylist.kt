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

    val canEdit: Boolean
        get() = source == LibrarySource.MediaStore.id && !isLikedSongs

    val canRemoveTracks: Boolean
        get() = canEdit || isLikedSongs

    companion object {
        const val LikedSongsId = "local:liked"
    }
}
