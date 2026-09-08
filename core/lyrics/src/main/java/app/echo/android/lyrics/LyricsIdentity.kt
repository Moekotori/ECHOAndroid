package app.echo.android.lyrics

import java.text.Normalizer
import java.util.Locale

internal fun String.lyricsMatchKey(): String = lyricsNormalized().lowercase(Locale.ROOT).replace(NonLetters, "")
private fun String.lyricsNormalized(): String = Normalizer.normalize(this, Normalizer.Form.NFKC)
private val NonLetters = Regex("""[^\p{L}\p{N}]+""")
private val BracketSuffix = Regex("""^(.*?)\s*[([]([^()\[\]]+)[)\]]\s*$""")
private val Latin = Regex("[A-Za-z]")
private val EastAsian = Regex("""[\p{IsHan}\p{IsHiragana}\p{IsKatakana}\p{IsHangul}]""")
private val CharacterCredit = Regex("""\bCV\s*[.:：]?\s*""", RegexOption.IGNORE_CASE)
private val ArtistSeparators = Regex("""\s*(?:[/;；、&＆×]|\s+[xX]\s+|\b(?:feat|ft|featuring)\.?(?![\p{L}\p{N}]))\s*""", RegexOption.IGNORE_CASE)

internal fun hasLyricsCharacterCredit(value: String): Boolean = CharacterCredit.containsMatchIn(value.lyricsNormalized())

/** Only explicit bilingual suffixes imply aliases. Ordinary parenthetical version labels stay intact. */
private fun bilingualNames(value: String): List<String> {
    val normalized = value.lyricsNormalized()
    val match = BracketSuffix.matchEntire(normalized) ?: return listOf(normalized)
    val (base, suffix) = match.destructured
    if (base.isBlank() || suffix.isBlank() || hasLyricsCharacterCredit(suffix) ||
        lyricsVersionKeys(suffix).isNotEmpty() || lyricsLanguageVersions(suffix, null).isNotEmpty()) return listOf(normalized)
    val differentScripts = (EastAsian.containsMatchIn(base) && Latin.containsMatchIn(suffix) && !EastAsian.containsMatchIn(suffix)) ||
        (Latin.containsMatchIn(base) && !EastAsian.containsMatchIn(base) && EastAsian.containsMatchIn(suffix))
    return if (differentScripts) listOf(base.trim(), suffix.trim(), normalized) else listOf(normalized)
}

internal fun lyricsTitleKeys(title: String, aliases: List<String> = emptyList()): Set<String> =
    (bilingualNames(title) + aliases.take(12).filter {
        lyricsVersionKeys(it).isEmpty() && lyricsLanguageVersions(it, null).isEmpty()
    }.flatMap(::bilingualNames)).map { it.lyricsMatchKey() }.filter(String::isNotBlank).toSet()

internal fun lyricsArtistGroups(artist: String, providerGroups: List<List<String>> = emptyList()): List<Set<String>> {
    val groups = providerGroups.ifEmpty { ArtistSeparators.split(artist.lyricsNormalized()).map(::listOf) }
    return groups.take(16).map { names ->
        names.take(12).flatMap { name ->
            val normal = name.lyricsNormalized()
            val credit = BracketSuffix.matchEntire(normal)?.groupValues?.get(2)
                ?.takeIf(::hasLyricsCharacterCredit)?.replace(CharacterCredit, "")
            bilingualNames(normal) + listOfNotNull(credit)
        }.map { it.lyricsMatchKey() }.filter(String::isNotBlank).toSet()
    }.filter(Set<String>::isNotEmpty)
}

/** Require an unambiguous one-to-one mapping; an alias never replaces an entire collaboration. */
internal fun lyricsArtistsMatch(target: List<Set<String>>, candidate: List<Set<String>>): Boolean {
    if (target.isEmpty() || target.size != candidate.size) return false
    val indices = target.map { names -> candidate.indices.filter { names.intersect(candidate[it]).isNotEmpty() } }
    return indices.all { it.size == 1 } && indices.map { it.single() }.distinct().size == target.size
}

/** At most one extra search, only when explicit bilingual metadata supplies a cleaner query. */
internal fun lyricsSearchFallback(request: EchoLyricsSearchRequest): EchoLyricsSearchRequest? {
    val titleNames = bilingualNames(request.title)
    val artistNames = ArtistSeparators.split(request.artist.lyricsNormalized()).map(::bilingualNames)
    if (titleNames.size == 1 && artistNames.all { it.size == 1 }) return null
    val title = titleNames.first()
    val artist = artistNames.joinToString(" / ") { it.first() }
    return request.copy(title = title, artist = artist).takeIf { it.title != request.title || it.artist != request.artist }
}

private val VersionPatterns = listOf(
    """(?<![a-z])live(?![a-z])|ライブ|ライヴ|라이브|现场|現場""",
    """(?<![a-z])remix(?![a-z])|リミックス|리믹스""",
    """(?<![a-z])(?:instrumental|karaoke|off[\s-]*vocal)(?![a-z])|カラオケ|インスト(?:ゥルメンタル)?|인스트루멘탈|伴奏|纯音乐|純音樂""",
    """(?<![a-z])(?:acoustic|unplugged)(?![a-z])|アコースティック|어쿠스틱|不插电|不插電""",
    """(?<![a-z])demo(?![a-z])|デモ|데모""",
    """(?<![a-z])radio[\s-]*edit(?![a-z])|ラジオエディット""",
    """(?<![a-z])tv[\s-]*(?:size|edit|version)(?![a-z])|テレビサイズ|TVサイズ|TV판""",
    """(?<![a-z])short[\s-]*(?:ver(?:sion)?|edit)(?![a-z])|ショート(?:バージョン|版)""",
    """(?<![a-z])extended(?![a-z])|エクステンデッド""",
    """(?<![a-z])sped[\s-]*up(?![a-z])""", """(?<![a-z])slowed(?![a-z])""",
    """(?<![a-z])remaster(?:ed)?(?![a-z])|リマスター|리마스터|重制|重製""",
    """(?<![a-z])mono(?![a-z])|モノラル""", """(?<![a-z])stereo(?![a-z])|ステレオ""",
).map { Regex(it, RegexOption.IGNORE_CASE) }

internal fun lyricsVersionKeys(value: String): Set<Int> {
    val normalized = value.lyricsNormalized()
    return VersionPatterns.indices.filterTo(mutableSetOf()) { VersionPatterns[it].containsMatchIn(normalized) }
}
