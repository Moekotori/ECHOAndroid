package app.echo.android.model.connect

data class EchoRemoteLibraryState(
    val isLoading: Boolean = false,
    /** 用户滚到列表末尾后，下一页正在读取。 */
    val isLoadingMore: Boolean = false,
    val query: String = "",
    val tracks: List<EchoRemoteTrack> = emptyList(),
    val albums: List<EchoRemoteAlbum> = emptyList(),
    val albumTotalCount: Int = 0,
    val albumTracks: Map<String, List<EchoRemoteTrack>> = emptyMap(),
    val loadingAlbumId: String? = null,
    val albumsUnavailable: Boolean = false,
    val playlists: List<EchoRemotePlaylist> = emptyList(),
    val playlistTotalCount: Int = 0,
    val playlistTracks: Map<String, List<EchoRemoteTrack>> = emptyMap(),
    val loadingPlaylistId: String? = null,
    val folders: List<EchoRemoteFolder> = emptyList(),
    val folderPath: String = "",
    val folderTracks: List<EchoRemoteTrack> = emptyList(),
    val foldersUnavailable: Boolean = false,
    val loadingFolderPath: String? = null,
    val totalCount: Int = 0,
    val error: String? = null,
)
