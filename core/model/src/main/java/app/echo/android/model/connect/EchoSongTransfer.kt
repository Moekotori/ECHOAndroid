package app.echo.android.model.connect

object EchoSongTransferPolicy {
    const val MaxFileBytes = 1024L * 1024 * 1024
    const val MaxBatchFiles = 32
    val extensions = setOf("flac", "fla", "mp3", "mp2", "m4a", "m4b", "aac", "alac", "ogg", "oga", "opus", "wav", "wave", "bwf",
        "aiff", "aif", "aifc", "ape", "wv", "wma", "asf", "dsf", "dff", "tta", "tak", "w64", "mka", "ac3", "eac3", "ec3", "dts", "amr", "adts")
    fun validFile(name: String, size: Long): Boolean = name.length in 1..240 &&
        name.none { it == '/' || it == '\\' || it.code < 32 } &&
        name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in extensions && size in 1..MaxFileBytes
}

data class EchoSongTransferItem(val id: String, val name: String, val size: Long, val bytes: Long = 0,
    val direction: String, val state: String = "queued")

data class EchoSongTransferState(val starting: Boolean = false, val active: Boolean = false,
    val directory: String? = null, val sending: Boolean = false, val current: EchoSongTransferItem? = null,
    val items: List<EchoSongTransferItem> = emptyList(), val error: String? = null, val received: Int = 0)
