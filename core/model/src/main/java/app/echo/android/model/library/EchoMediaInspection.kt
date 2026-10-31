package app.echo.android.model.library

data class EchoEncodingPreview(
    val charset: String?, val title: String, val artist: String,
    val album: String?, val albumArtist: String?, val canRestore: Boolean = false, val sourceRevision: String = "",
)

data class EchoAlbumImage(val id: String, val uri: String, val kind: Int = 0, val name: String? = null)

data class EchoFolderEncodingItem(val trackId: String, val title: String, val preview: EchoEncodingPreview?)
data class EchoFolderEncodingResult(val applied: Int, val failed: Int)

data class EchoDiscoveredPlaylist(
    val uri: String, val name: String, val fingerprint: String,
    val preview: PlaylistImportPreview?, val alreadyImported: Boolean = false,
    val error: Boolean = false,
    val existingUpdatedAt: Long? = null,
)

data class EchoPlaylistDiscoveryResult(val items: List<EchoDiscoveredPlaylist>, val truncated: Boolean = false,
    val unavailableDirectories: Int = 0)

object EchoTextEncodings {
    val names = listOf("UTF-8", "GB18030", "Big5", "Shift_JIS", "EUC-KR", "windows-1252", "UTF-16LE", "UTF-16BE")
}
