package app.echo.android.model.settings

enum class EchoPerformanceMode(val id: String) {
    Auto("auto"),
    Balanced("balanced"),
    Lightweight("lightweight"),
    ;

    fun resolve(systemPowerSaveMode: Boolean): EchoEffectivePerformanceMode =
        when {
            this == Lightweight -> EchoEffectivePerformanceMode.Lightweight
            this == Auto && systemPowerSaveMode -> EchoEffectivePerformanceMode.Lightweight
            else -> EchoEffectivePerformanceMode.Balanced
        }

    companion object {
        fun fromId(value: String?): EchoPerformanceMode =
            entries.firstOrNull { it.id == value } ?: Auto
    }
}

enum class EchoEffectivePerformanceMode(val id: String) {
    Balanced("balanced"),
    Lightweight("lightweight"),
    ;

    val isLightweight: Boolean
        get() = this == Lightweight
}
