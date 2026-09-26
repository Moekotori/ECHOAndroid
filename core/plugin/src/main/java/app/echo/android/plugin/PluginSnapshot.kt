package app.echo.android.plugin

data class PluginSummary(
    val id: String,
    val name: String,
    val version: String,
    val summary: String,
    val enabled: Boolean,
    val requested: List<PluginCapability>,
    val grants: Set<PluginCapability>,
    val errorCode: String?,
    val errorDetail: String?,
    val logs: List<String>,
    val page: PluginPageDocument?,
)

data class PluginsSnapshot(
    val loaded: Boolean = false,
    val plugins: List<PluginSummary> = emptyList(),
)

sealed class PluginInstallResult {
    data class Installed(val id: String) : PluginInstallResult()
    data class Rejected(val reason: PluginRejectReason) : PluginInstallResult()
}

enum class PluginRejectReason {
    Unreadable,
    TooLarge,
    TooManyFiles,
    UnsafePath,
    MissingManifest,
    InvalidManifest,
    MissingEntry,
    Io,
}
