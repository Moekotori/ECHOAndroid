package app.echo.android.lyrics

import java.util.Locale

/** Exact names win; separator-tolerant fallback must identify a single sibling. */
object LyricsSidecarNames {
    fun candidates(fileName: String?, title: String, preferTitle: Boolean = false): List<String> =
        listOfNotNull(fileName?.substringBeforeLast('.')?.takeIf(String::isNotBlank), title.takeIf(String::isNotBlank))
            .let { if (preferTitle) it.reversed() else it }
            .filter { '/' !in it && '\\' !in it && '\u0000' !in it && it != "." && it != ".." }
            .distinct().flatMap { base -> EchoLyricsParser.fileExtensions.map { "$base$it" } }

    fun isAudioSidecar(label: String?, audioFileName: String?): Boolean =
        label != null && audioFileName != null &&
            key(label.substringAfterLast('/').substringBeforeLast('.')) == key(audioFileName.substringBeforeLast('.'))

    fun matching(candidates: List<String>, files: Collection<String>, complete: Boolean = true): List<String> {
        val original = files.toSet()
        val exact = files.groupBy { it.lowercase(Locale.ROOT) }
        val normalized = files.groupBy(::key)
        return candidates.flatMap { name ->
            when {
                name in original -> listOf(name)
                !complete -> emptyList()
                else -> (exact[name.lowercase(Locale.ROOT)]?.singleOrNull()
                    ?: normalized[key(name)]?.singleOrNull())?.let(::listOf).orEmpty()
            }
        }.distinct()
    }

    private fun key(name: String): String = name.trim().lowercase(Locale.ROOT).replace(separators, "")
    private val separators = Regex("[\\s._\\-]+")
}
