package app.echo.android.model.connect

data class EchoRemoteLibraryState(
    val isLoading: Boolean = false,
    val query: String = "",
    val tracks: List<EchoRemoteTrack> = emptyList(),
    val totalCount: Int = 0,
    val error: String? = null,
)
