package app.echo.android.model.connect

data class EchoRemoteFolder(
    val path: String,
    val name: String,
    val trackCount: Int = 0,
    val childFolderCount: Int = 0,
    val artworkUrl: String? = null,
)
