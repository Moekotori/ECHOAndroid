package app.echo.android.model.connect

object EchoWifiTransferPolicy {
    const val MaxFileBytes = 1024L * 1024 * 1024
    const val MaxSessionBytes = 8L * 1024 * 1024 * 1024
    const val MaxFiles = 500
    const val SessionDurationMs = 20L * 60 * 1000
    fun pathSegments(path: String): List<String> {
        require(path.length in 1..512 && !path.contains('\\') && path.none { it.isISOControl() }) { "invalid_path" }
        val segments = path.split('/')
        require(segments.size <= 9 && segments.all {
            it.isNotBlank() && it.length <= 120 && it != "." && it != ".." && !it.startsWith('.') &&
                it == it.trim() && it.none { c -> c in ":*?\"<>|" }
        }) { "invalid_path" }
        return segments
    }
}

data class EchoWifiTransferResult(val name: String, val success: Boolean, val reason: String? = null)

data class EchoWifiTransferState(
    val phase: String = "idle",
    val directory: String? = null,
    val address: String? = null,
    val pairingCode: String? = null,
    val receiving: String? = null,
    val completed: Int = 0,
    val failed: Int = 0,
    val results: List<EchoWifiTransferResult> = emptyList(),
    val error: String? = null,
)
