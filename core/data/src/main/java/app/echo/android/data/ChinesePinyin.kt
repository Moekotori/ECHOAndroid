package app.echo.android.data

internal object ChinesePinyin {
    private val outputFormat = net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat().apply {
        caseType = net.sourceforge.pinyin4j.format.HanyuPinyinCaseType.LOWERCASE
        toneType = net.sourceforge.pinyin4j.format.HanyuPinyinToneType.WITHOUT_TONE
        vCharType = net.sourceforge.pinyin4j.format.HanyuPinyinVCharType.WITH_V
    }

    fun toPinyin(text: String): String {
        if (text.isEmpty()) return ""
        val full = StringBuilder(text.length * 6)
        val initials = StringBuilder(text.length)
        try {
            for (ch in text) {
                val pinyinArray = net.sourceforge.pinyin4j.PinyinHelper.toHanyuPinyinStringArray(
                    ch,
                    outputFormat,
                )
                if (pinyinArray != null && pinyinArray.isNotEmpty()) {
                    val syllable = pinyinArray[0]
                    full.append(syllable)
                    initials.append(syllable.first())
                } else if (ch.isLetterOrDigit()) {
                    val normalized = ch.lowercaseChar()
                    full.append(normalized)
                    initials.append(normalized)
                }
            }
        } catch (e: Exception) {
            return text.lowercase()
        }
        return buildList {
            val fullPinyin = full.toString()
            val initialsPinyin = initials.toString()
            if (fullPinyin.isNotBlank()) add(fullPinyin)
            if (initialsPinyin.isNotBlank() && initialsPinyin != fullPinyin) add(initialsPinyin)
        }.joinToString(" ")
    }
}
