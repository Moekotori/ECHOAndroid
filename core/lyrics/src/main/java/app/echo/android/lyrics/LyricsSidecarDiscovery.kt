package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricAlternateText
import app.echo.android.model.lyrics.EchoLyrics
import java.text.Normalizer
import java.util.Locale

data class LyricsSidecarFiles(
    val primary: String,
    val translation: String? = null,
    val romanization: String? = null,
    val language: String? = null,
)

/** Pure discovery over a bounded directory snapshot; paths are relative to the audio directory. */
object LyricsSidecarDiscovery {
    private val languageTag = Regex("""[._-]([^\s._/\\-]{2,12})$""")
    private val romanTags = setOf("rom", "roman", "romanised", "romanization", "romanisation", "romanized", "lr", "py", "pinyin", "罗马音", "罗马字", "拼音")
    private val translationTags = setOf("zh", "cn", "chn", "chi", "chinese", "tw", "cht", "hant", "hans", "hk", "en", "eng", "ja", "jp", "jpn", "ko", "kr", "kor", "fr", "fra", "de", "deu", "es", "spa", "it", "ita", "pt", "por", "ru", "rus", "th", "vi", "vie", "id", "中文", "译文", "翻译", "中译", "英文", "日文", "韩文")
    private val separators = Regex("[\\s._-]+")
    private fun key(value: String) = Normalizer.normalize(value, Normalizer.Form.NFC).lowercase(Locale.ROOT).replace(separators, "")
    private fun extension(path: String) = EchoLyricsParser.fileExtensions.firstOrNull { path.endsWith(it, true) }

    internal fun untaggedStem(stem: String): String {
        val match = languageTag.find(stem) ?: return stem
        return if (match.groupValues[1].lowercase(Locale.ROOT) in romanTags + translationTags)
            stem.substring(0, match.range.first) else stem
    }

    fun discover(candidates: List<String>, files: Collection<String>, complete: Boolean = true): List<LyricsSidecarFiles> {
        val entries = files.asSequence().filter { path ->
            val parts = path.split('/')
            parts.size in 1..2 && (parts.size == 1 || parts[0] == "lyrics") && parts.all { it.isNotBlank() && it != "." && it != ".." && '\\' !in it && '\u0000' !in it }
        }.take(8192).distinct().toList()
        val literal = entries.toSet()
        val folded = entries.groupBy { it.lowercase(Locale.ROOT) }
        fun exact(path: String): String? = if (path in literal) path else
            if (complete) folded[path.lowercase(Locale.ROOT)]?.singleOrNull() else null
        val wanted = listOf("", "lyrics/").flatMap { folder -> candidates.map { folder + it } }
        val primary = wanted.mapNotNull(::exact).toMutableList()
        if (complete) primary += LyricsSidecarNames.matching(wanted, entries).filterNot { it in primary }
        val stems = candidates.map { it.substringBeforeLast('.') }.distinct()
        val allowedExtensions = candidates.mapNotNull(::extension).toSet()

        data class Tagged(val path: String, val base: String, val role: String, val language: String)
        val tagged = if (!complete) emptyList() else entries.mapNotNull { path ->
            val ext = extension(path) ?: return@mapNotNull null
            val stem = path.substringAfterLast('/').dropLast(ext.length)
            val match = languageTag.find(stem) ?: return@mapNotNull null
            val tag = match.groupValues[1].lowercase(Locale.ROOT)
            val role = when (tag) { in romanTags -> "romanization"; in translationTags -> "translation"; else -> return@mapNotNull null }
            Tagged(path, stem.substring(0, match.range.first), role, tag)
        }
        fun language(stem: String, role: String, except: String): Tagged? = tagged.firstOrNull {
            it.path != except && it.role == role && key(it.base) == key(stem)
        }
        fun companions(path: String, promotedLanguage: String? = null): LyricsSidecarFiles {
            val ext = extension(path).orEmpty()
            val folder = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
            val stem = path.substringAfterLast('/').dropLast(ext.length)
            val base = tagged.firstOrNull { it.path == path }?.base?.takeIf { promotedLanguage != null } ?: stem
            fun companion(marker: String, role: String): String? {
                if (ext != ".txt") {
                    val preferred = listOf(ext) + EchoLyricsParser.fileExtensions.filter { it != ext && it != ".txt" }
                    val names = preferred.map { "$folder$base.$marker$it" }
                    names.firstNotNullOfOrNull(::exact)?.let { return it }
                    if (complete) LyricsSidecarNames.matching(names, entries).firstOrNull()?.let { return it }
                }
                return language(base, role, path)?.path
            }
            return LyricsSidecarFiles(path,
                if (promotedLanguage == null) companion("t", "translation") else null,
                companion("r", "romanization"), promotedLanguage)
        }
        if (primary.isNotEmpty()) return primary.distinct().map { companions(it) }
        val promoted = stems.firstNotNullOfOrNull { stem ->
            tagged.firstOrNull { key(it.base) == key(stem) && it.role == "translation" && extension(it.path) in allowedExtensions }
                ?: tagged.firstOrNull { key(it.base) == key(stem) && it.role == "romanization" && extension(it.path) in allowedExtensions }
        }
        if (promoted != null) return listOf(companions(promoted.path, promoted.language))
        if (complete) {
            val numbered = entries.filter { path ->
                val ext = extension(path) ?: return@filter false
                if (ext !in allowedExtensions) return@filter false
                val stem = path.substringAfterLast('/').dropLast(ext.length)
                val without = stem.replace(Regex("""^\d{1,3}(?:[.-] *| +(?:[-.] *)?)"""), "")
                without != stem && stems.any { key(it) == key(without) }
            }
            if (numbered.size == 1) return listOf(companions(numbered.single()))
        }
        return emptyList()
    }

    /** ECHOSteam matches each timed companion once within 350 ms; plain tracks use row order. */
    fun merge(primary: EchoLyrics, translated: EchoLyrics?, romanized: EchoLyrics?): EchoLyrics {
        fun rows(value: EchoLyrics?): List<String?> {
            if (value == null) return List(primary.lines.size) { null }
            if (!value.isSynced) return primary.lines.indices.map { value.lines.getOrNull(it)?.text }
            fun clean(text: String) = text.replace(Regex("\\s+"), " ").trim()
            val available = java.util.TreeMap<Long, java.util.ArrayDeque<String>>()
            value.lines.filter { it.startMs >= 0 && it.text.isNotBlank() }.forEach { line ->
                available.getOrPut(line.startMs) { java.util.ArrayDeque() }.add(clean(line.text))
            }
            return primary.lines.map { line ->
                if (line.startMs < 0) return@map null
                val stamp = listOfNotNull(available.floorKey(line.startMs), available.ceilingKey(line.startMs))
                    .minByOrNull { kotlin.math.abs(it - line.startMs) }
                    ?.takeIf { kotlin.math.abs(it - line.startMs) <= 350 }
                if (stamp == null) null else {
                    val queue = available.getValue(stamp)
                    queue.removeFirst().also { if (queue.isEmpty()) available.remove(stamp) }
                }
            }
        }
        val translations = rows(translated)
        val romanizations = rows(romanized)
        return primary.copy(lines = primary.lines.mapIndexed { index, line ->
            val translation = translations[index]?.takeIf(String::isNotBlank)
            val romanization = romanizations[index]?.takeIf(String::isNotBlank)
            line.copy(translation = translation ?: line.translation, romanization = romanization ?: line.romanization,
                alternateTexts = (line.alternateTexts + listOfNotNull(
                    translation?.let { EchoLyricAlternateText("translation", it) },
                    romanization?.let { EchoLyricAlternateText("romanization", it) },
                )).distinct().take(8))
        })
    }
}
