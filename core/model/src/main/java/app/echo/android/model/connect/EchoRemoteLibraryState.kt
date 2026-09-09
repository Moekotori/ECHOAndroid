package app.echo.android.model.connect

data class EchoRemoteLibraryState(
    val isLoading: Boolean = false,
    /** 首页已展示,后续页仍在后台流式拉取中 */
    val isLoadingMore: Boolean = false,
    val query: String = "",
    val tracks: List<EchoRemoteTrack> = emptyList(),
    val albums: List<EchoRemoteAlbum> = emptyList(),
    val albumTracks: Map<String, List<EchoRemoteTrack>> = emptyMap(),
    val loadingAlbumId: String? = null,
    val albumsUnavailable: Boolean = false,
    val playlists: List<EchoRemotePlaylist> = emptyList(),
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
