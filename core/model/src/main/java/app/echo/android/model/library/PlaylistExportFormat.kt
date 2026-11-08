package app.echo.android.model.library

enum class PlaylistExportFormat(val extension: String, val mimeType: String) {
    M3u("m3u8", "audio/x-mpegurl"), Text("txt", "text/plain"), Csv("csv", "text/csv");
}
