package app.echo.android.model.connect

/** Public library metadata only: no URI, endpoint or session credential. */
data class EchoPhoneCatalogQuery(
    val kind: String, val page: Int = 1, val search: String = "",
    val parentKind: String? = null, val parentId: String? = null,
) {
    init {
        require(kind in setOf("songs", "albums", "artists", "folders", "playlists", "liked", "genres"))
        require(page in 1..100_000 && search.length <= 256)
        require(parentKind == null || parentKind in setOf("album", "artist", "folder", "playlist", "genre"))
        require((parentKind == null) == (parentId == null))
        require(parentId == null || parentId.length in 1..2048)
    }
}

data class EchoPhoneCatalogEntry(
    val kind: String, val id: String, val title: String, val subtitle: String = "",
    val count: Int = 0, val coverId: String? = null, val artist: String? = null,
    val album: String? = null, val durationMs: Long? = null, val canPlay: Boolean? = null,
    val sampleRate: Int? = null,
)
data class EchoPhoneCatalogPage(val entries: List<EchoPhoneCatalogEntry>, val total: Int, val page: Int,
    val parentKind: String? = null, val parentId: String? = null)
