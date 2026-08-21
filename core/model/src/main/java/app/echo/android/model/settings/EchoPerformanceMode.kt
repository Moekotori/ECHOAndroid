package app.echo.android.model.settings

enum class EchoPerformanceMode(val id: String) {
    Auto("auto"),
    Balanced("balanced"),
    Lightweight("lightweight"),
    HighPerformance("high_performance"),
    ;

    fun resolve(systemPowerSaveMode: Boolean): EchoEffectivePerformanceMode =
        when {
            this == Lightweight -> EchoEffectivePerformanceMode.Lightweight
            this == Auto && systemPowerSaveMode -> EchoEffectivePerformanceMode.Lightweight
            this == HighPerformance && systemPowerSaveMode -> EchoEffectivePerformanceMode.Balanced
            this == HighPerformance -> EchoEffectivePerformanceMode.HighPerformance
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
    HighPerformance("high_performance"),
    ;

    val isLightweight: Boolean
        get() = this == Lightweight

    val isBalanced: Boolean
        get() = this == Balanced

    val isHighPerformance: Boolean
        get() = this == HighPerformance

    val prefersHighRefreshRate: Boolean
        get() = this == HighPerformance
}
