package app.echo.android.model.library

enum class NeteaseAudioQuality(
    val id: String,
    val label: String,
    val bitrateKbps: Int,
) {
    Standard("standard", "标准", 128),
    Higher("higher", "较高", 320),
    ExHigh("exhigh", "极高", 320),
    Lossless("lossless", "无损", 999),
    HiRes("hires", "Hi-Res", 1999),
    ;

    companion object {
        val Default = Lossless

        fun fromId(value: String?): NeteaseAudioQuality =
            entries.firstOrNull { it.id == value } ?: Default
    }
}

data class NeteaseAccountState(
    val loggedIn: Boolean = false,
    val userId: Long? = null,
    val nickname: String? = null,
    val playlists: List<NeteaseRemotePlaylist> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

data class NeteaseImportState(
    val importing: Boolean = false,
    val playlistName: String? = null,
    val scannedCount: Int = 0,
    val importedCount: Int = 0,
    val message: String? = null,
    val error: String? = null,
)

data class NeteaseRemotePlaylist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val artworkUri: String? = null,
    val creatorName: String? = null,
)
