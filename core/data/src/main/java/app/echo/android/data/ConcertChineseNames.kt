package app.echo.android.data

/** Fixed OpenCC t2s dictionaries. Loaded once on the concert IO path; no per-name cache. */
internal object ConcertChineseNames {
    private data class Rule(val from: String, val to: String)
    private val rules by lazy {
        val entries = LinkedHashMap<String, String>()
        for (file in listOf("TSCharacters.txt", "TSPhrases.txt")) {
            val path = "/app/echo/android/data/concert-matching/opencc/$file"
            checkNotNull(javaClass.getResourceAsStream(path)) { "Missing concert name dictionary: $file" }
                .bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.filter { it.isNotBlank() && !it.startsWith('#') }.forEach { line ->
                        val parts = line.split('\t', limit = 2)
                        check(parts.size == 2 && parts[0].isNotEmpty()) { "Invalid concert name dictionary" }
                        // OpenCC's first candidate is the default; phrase rules override character rules.
                        entries[parts[0]] = parts[1].substringBefore(' ')
                    }
                }
        }
        entries.map { Rule(it.key, it.value) }.groupBy { it.from.codePointAt(0) }
            .mapValues { (_, values) -> values.sortedByDescending { it.from.length } }
    }

    fun simplified(value: String): String {
        if (value.codePoints().noneMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }) return value
        val dictionary = rules
        return buildString(value.length) {
            var index = 0
            while (index < value.length) {
                val point = value.codePointAt(index)
                val rule = dictionary[point]?.firstOrNull { value.startsWith(it.from, index) }
                if (rule != null) {
                    append(rule.to)
                    index += rule.from.length
                } else {
                    appendCodePoint(point)
                    index += Character.charCount(point)
                }
            }
        }
    }
}
