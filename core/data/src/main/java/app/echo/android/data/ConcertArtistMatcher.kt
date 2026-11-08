package app.echo.android.data

import java.text.Normalizer
import java.util.Locale

/** Compile once per provider read, then reuse over the bounded feed/listing. */
internal class ConcertArtistMatcher(names: List<String>) {
    private val keys = searchNames(names).map(::key).filter { it.isNotEmpty() }.toSet()
    private val titles = keys.filter { it.codePointCount(0, it.length) >= 2 }.map { name ->
        val suffix = if (name.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN })
            "|演唱会|音乐会|独奏会|巡回|巡演|钢琴|小提琴|大提琴|吉他" else ""
        Regex("(?<!\\p{L})${Regex.escape(name)}(?=$|[^\\p{L}\\p{N}]$suffix)")
    }

    fun matchesName(name: String): Boolean = key(name) in keys

    fun matchesTitle(title: String): Boolean {
        val text = key(title)
        return !tribute.containsMatchIn(text) && titles.any { it.containsMatchIn(text) }
    }

    fun matches(record: RegionalConcertParser.Record): Boolean {
        // With a supplied lineup the title must not override a different artist identity.
        if (record.event.details.lineup.isNotEmpty()) return record.event.details.lineup.any { matchesName(it.name) }
        // A bilingual title without the word 'tribute' must not undo the other title's warning.
        if (record.titles.any { tribute.containsMatchIn(key(it)) }) return false
        return record.titles.any(::matchesTitle)
    }

    companion object {
        private val qualifier = Regex("\\s*[（(][^（）()]+[）)]\\s*$")
        private val latinMarks = Regex("(?<=\\p{IsLatin})\\p{M}+")
        private val separators = Regex("[^\\p{L}\\p{N}]+")
        private val tribute = Regex("致敬|tribute")

        fun key(value: String): String {
            val compatible = Normalizer.normalize(value, Normalizer.Form.NFKC)
            val decomposed = Normalizer.normalize(compatible, Normalizer.Form.NFD).replace(latinMarks, "")
            // Recompose kana voicing marks: folding Latin accents must not conflate が with か.
            val composed = Normalizer.normalize(decomposed, Normalizer.Form.NFC)
            return ConcertChineseNames.simplified(composed).lowercase(Locale.ROOT).replace(separators, " ").trim()
        }

        fun searchNames(names: List<String>): List<String> = names.asSequence().take(16)
            .flatMap { name -> sequenceOf(name.trim(), name.replace(qualifier, "").trim()) }
            .filter { it.isNotBlank() }
            // Keep script variants for provider searches even when their matching keys are equal.
            .distinctBy { Normalizer.normalize(it, Normalizer.Form.NFKC).lowercase(Locale.ROOT) }.take(16).toList()
    }
}
