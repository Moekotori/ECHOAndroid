package app.echo.android.plugin

internal object PluginPaths {
    private val PluginId = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
    private val ActionId = Regex("^[A-Za-z0-9_-]{1,40}$")

    fun isPluginId(value: String): Boolean = PluginId.matches(value)

    fun isActionId(value: String): Boolean = ActionId.matches(value)

    fun isScriptEntry(value: String): Boolean {
        val relative = safeRelative(value) ?: return false
        return relative.endsWith(".js") && relative.length <= 120
    }

    fun isOpaqueTrackId(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= 80 &&
            '/' !in value &&
            '\\' !in value &&
            "://" !in value

    /** Zip entry name that stays inside the package directory, or null. */
    fun safeRelative(value: String): String? {
        val normalized = value.replace('\\', '/')
        if (normalized.isBlank() || normalized.length > 180) return null
        if (normalized.startsWith("/") || normalized.endsWith("/") || ':' in normalized) return null
        val parts = normalized.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }
}
