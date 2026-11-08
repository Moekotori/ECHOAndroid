package app.echo.android.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicReference

object EchoArtworkUrlRewriteRegistry {
    private val rewrite = AtomicReference<(String) -> String>({ it })
    private val resolved = object : LinkedHashMap<String, String>(128, .75f, true) {}
    private val lock = Any()
    var revision by mutableIntStateOf(0)
        private set

    fun replace(block: (String) -> String) {
        rewrite.set(block)
        notifyChanged()
    }

    fun notifyChanged() {
        synchronized(lock) { resolved.clear() }
        revision += 1
    }

    fun rewrite(url: String?): String? {
        val raw = url?.takeIf { it.isNotBlank() } ?: return url
        return synchronized(lock) {
            resolved[raw] ?: rewrite.get().invoke(raw).also { value ->
                resolved[raw] = value
                while (resolved.size > 512) resolved.remove(resolved.keys.first())
            }
        }
    }
}

internal fun resolvedArtworkFetchUri(original: String?): String? {
    val resolved = EchoArtworkUrlRewriteRegistry.rewrite(original) ?: return null
    if (original.isNullOrBlank()) return null
    if (isLikelySubsonicRestUrl(original) && resolved == original) return null
    return resolved
}

internal fun isLikelySubsonicRestUrl(url: String): Boolean {
    val lower = url.lowercase()
    return "/rest/" in lower || lower.contains("/rest?")
}
