package app.echo.android.data

import java.util.Locale

/** Ancestor membership is proportional to path depth, not the number of failed directories. */
internal class ScanDirectoryIndex(paths: Collection<String> = emptyList(), private val ignoreCase: Boolean = false) {
    private val roots = paths.mapTo(HashSet(), ::normalize)
    fun add(path: String) { roots += normalize(path) }
    fun contains(path: String?): Boolean {
        var current = normalize(path.orEmpty())
        while (true) {
            if (current in roots) return true
            if (current.isEmpty()) return false
            current = current.substringBeforeLast('/', "")
        }
    }
    private fun normalize(path: String): String = path.replace('\\', '/').trim('/').let {
        if (ignoreCase) it.lowercase(Locale.ROOT) else it
    }
}
