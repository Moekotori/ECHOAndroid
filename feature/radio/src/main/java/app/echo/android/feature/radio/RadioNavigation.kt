package app.echo.android.feature.radio

internal object RadioNavigation {
    private val musicTags = setOf("pop", "rock", "jazz", "classical", "electronic", "easy listening", "folk", "blues", "country", "hip hop", "j-pop", "jrock", "anime", "vocaloid", "touhou", "doujin", "vgm", "chiptune")
    fun parent(view: String, tag: String): String = when {
        view == "results" && tag in musicTags -> "music"
        view == "results" && tag in setOf("news", "talk") -> "spoken"
        else -> "home"
    }
}
