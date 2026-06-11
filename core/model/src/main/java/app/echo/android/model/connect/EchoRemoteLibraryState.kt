package app.echo.android.model.connect

data class EchoRemoteLibraryState(
    val isLoading: Boolean = false,
    val query: String = "",
    val tracks: List<EchoRemoteTrack> = emptyList(),
    val playlists: List<EchoRemotePlaylist> = emptyList(),
    val playlistTracks: Map<String, List<EchoRemoteTrack>> = emptyMap(),
    val loadingPlaylistId: String? = null,
    val totalCount: Int = 0,
    val error: String? = null,
)
