package app.echo.android.feature.library

/** Owned by the library screen; both entry count and retained text size are bounded. */
internal class LinkedLibrarySearchTextCache(
    private val maxEntries: Int = 4096,
    private val maxChars: Int = 1_000_000,
    private val transliterate: (String) -> String = ::linkedLibrarySearchPinyin,
) {
    private val entries = LinkedHashMap<String, String>(16, 0.75f, true)
    private var retainedChars = 0

    fun text(vararg parts: String): String = buildString {
        parts.forEach { part ->
            if (part.isNotBlank()) {
                if (isNotEmpty()) append(' ')
                append(normalizedPart(part))
            }
        }
    }

    private fun normalizedPart(part: String): String {
        synchronized(entries) { entries[part]?.let { return it } }
        val value = buildString {
            append(part.lowercase())
            val pinyin = transliterate(part)
            if (pinyin.isNotBlank()) {
                append(' ')
                append(pinyin)
            }
        }
        val cost = part.length + value.length
        if (maxEntries <= 0 || cost > maxChars) return value
        synchronized(entries) {
            entries[part]?.let { return it }
            entries[part] = value
            retainedChars += cost
            val oldest = entries.entries.iterator()
            while (entries.size > maxEntries || retainedChars > maxChars) {
                val entry = oldest.next()
                retainedChars -= entry.key.length + entry.value.length
                oldest.remove()
            }
        }
        return value
    }
}

private fun linkedLibrarySearchPinyin(text: String): String {
    if (text.isBlank()) return ""
    if (text.none(Char::needsPinyin)) return text.lowercase()
    val full = StringBuilder(text.length * 6)
    val initials = StringBuilder(text.length)
    return try {
        val outputFormat = net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat().apply {
            caseType = net.sourceforge.pinyin4j.format.HanyuPinyinCaseType.LOWERCASE
            toneType = net.sourceforge.pinyin4j.format.HanyuPinyinToneType.WITHOUT_TONE
            vCharType = net.sourceforge.pinyin4j.format.HanyuPinyinVCharType.WITH_V
        }
        text.forEach { ch ->
            val pinyinArray = net.sourceforge.pinyin4j.PinyinHelper.toHanyuPinyinStringArray(ch, outputFormat)
            if (!pinyinArray.isNullOrEmpty()) {
                val syllable = pinyinArray[0]
                full.append(syllable)
                initials.append(syllable.first())
            } else if (ch.isLetterOrDigit()) {
                val normalized = ch.lowercaseChar()
                full.append(normalized)
                initials.append(normalized)
            }
        }
        buildList {
            val fullPinyin = full.toString()
            val initialsPinyin = initials.toString()
            if (fullPinyin.isNotBlank()) add(fullPinyin)
            if (initialsPinyin.isNotBlank() && initialsPinyin != fullPinyin) add(initialsPinyin)
        }.joinToString(" ")
    } catch (_: Exception) {
        text.lowercase()
    }
}

private fun Char.needsPinyin(): Boolean =
    this in '\u3400'..'\u4DBF' ||
        this in '\u4E00'..'\u9FFF' ||
        this in '\uF900'..'\uFAFF'
