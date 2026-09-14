package app.echo.android.data

import app.echo.android.model.library.LibraryScanOptions
import org.json.JSONArray
import org.json.JSONObject

internal fun normalizeScanExcludedPath(path: String): String? =
    path.trim().replace('\\', '/').trim('/').split('/').filter { it.isNotBlank() }
        .takeIf { it.isNotEmpty() && it.none { part -> part == "." || part == ".." } }
        ?.joinToString("/")

internal fun LibraryScanOptions.normalizedForStorage(): LibraryScanOptions = copy(
    allowedExtensions = allowedExtensions.map { it.trim().removePrefix(".").lowercase(java.util.Locale.ROOT) }
        .filter { it.isNotEmpty() }.toSet(),
    minDurationMs = minDurationMs.coerceAtLeast(0),
    minSizeBytes = minSizeBytes.coerceAtLeast(0),
    excludedRelativePaths = excludedRelativePaths.mapNotNull(::normalizeScanExcludedPath).toSet(),
)

internal fun encodeLibraryScanOptions(options: LibraryScanOptions): String =
    options.normalizedForStorage().let { value ->
        JSONObject().apply {
            put("minDurationMs", value.minDurationMs)
            put("minSizeBytes", value.minSizeBytes)
            put("excludeNonMusicFolders", value.excludeNonMusicFolders)
            put("excludeHiddenFolders", value.excludeHiddenFolders)
            put("allowedExtensions", JSONArray(value.allowedExtensions.toList()))
            put("excludedRelativePaths", JSONArray(value.excludedRelativePaths.toList()))
        }.toString()
    }

internal fun decodeLibraryScanOptions(raw: String?): LibraryScanOptions {
    val defaults = LibraryScanOptions()
    if (raw.isNullOrBlank()) return defaults
    return runCatching {
        val obj = JSONObject(raw)
        val paths = obj.optJSONArray("excludedRelativePaths")
        val extensions = obj.optJSONArray("allowedExtensions")
        defaults.copy(
            allowedExtensions = (0 until (extensions?.length() ?: 0))
                .mapNotNull { extensions?.optString(it) }.toSet(),
            minDurationMs = obj.optLong("minDurationMs", defaults.minDurationMs),
            minSizeBytes = obj.optLong("minSizeBytes", defaults.minSizeBytes),
            excludeNonMusicFolders = obj.optBoolean("excludeNonMusicFolders", true),
            excludeHiddenFolders = obj.optBoolean("excludeHiddenFolders", true),
            excludedRelativePaths = (0 until (paths?.length() ?: 0))
                .mapNotNull { paths?.optString(it) }.toSet(),
        ).normalizedForStorage()
    }.getOrDefault(defaults)
}
