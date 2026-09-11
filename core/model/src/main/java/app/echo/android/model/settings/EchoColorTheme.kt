package app.echo.android.model.settings

enum class EchoColorTheme(val id: String) {
    Echo("echo"),
    Twilight("twilight"),
    Rosewood("rosewood"),
    Amber("amber"),
    Ocean("ocean"),
    Graphite("graphite"),
    Indigo("indigo"),
    Plum("plum"),
    Copper("copper"),
    Frost("frost"),
    ;

    companion object {
        val Default = Echo

        fun fromId(value: String?): EchoColorTheme =
            entries.firstOrNull { it.id == value } ?: Default
    }
}
