package app.echo.android.lyrics

import java.text.Normalizer

/** Only explicit language-version labels count; country/edition names do not imply singing language. */
internal fun lyricsLanguageVersions(title: String, album: String?): Set<Int> {
    fun extract(value: String): Set<Int> {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        return LanguageVersionPatterns.indices.filterTo(mutableSetOf()) {
            LanguageVersionPatterns[it].containsMatchIn(normalized)
        }
    }
    // Track-specific labels take precedence over a release-wide album label.
    return extract(title).ifEmpty { extract(album.orEmpty()) }
}

private val LanguageVersionPatterns = listOf(
    """japanese|jpn|jp|日本語|日语|日語|일본어|ジャパニーズ""",
    """korean|kor|kr|韓国語|韩语|韓語|한국어|한국말|コリアン""",
    """english|eng|en|英語|英语|영어|イングリッシュ""",
    """chinese|mandarin|chn|zh|中文|中国語|普通话|普通話|国语|國語|중국어""",
    """cantonese|yue|粤语|粵語|広東語|廣東話|广东话|광둥어""",
).map { language ->
    Regex(
        """(?<![\p{L}\p{N}])(?:$language)[\s._-]*(?:ver(?:sion)?\.?|버전|版|バージョン)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE,
    )
}
