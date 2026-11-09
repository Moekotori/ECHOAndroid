package app.echo.android.model.library

enum class PlaylistExportFormat(val extension: String, val mimeType: String) {
    M3u("m3u8", "audio/x-mpegurl"),
    LegacyM3u("m3u", "audio/x-mpegurl"),
    Pls("pls", "audio/x-scpls"),
    Xspf("xspf", "application/xspf+xml"),
    Wpl("wpl", "application/vnd.ms-wpl"),
    Asx("asx", "video/x-ms-asf"),
    Json("json", "application/json"),
    Csv("csv", "text/csv"),
    Text("txt", "text/plain"),
}
