package app.echo.android.model.connect

data class EchoRemoteQueueState(
    val items: List<EchoRemoteTrack> = emptyList(),
    val totalCount: Int = 0,
    val revision: Long? = null,
    val currentQueueId: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isMoving: Boolean = false,
    val error: String? = null,
    val unsupported: Boolean = false,
    val offset: Int = 0,
)
