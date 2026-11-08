package app.echo.android.model.settings

enum class EchoLyricsSource(val id: String) {
    Spl("spl"), Sidecar("sidecar"), Embedded("embedded");
}

/** Independent display destinations; disabling notifications must not stop lyric snapshots. */
data class EchoLyricsOptions(
    val sourceOrder: List<EchoLyricsSource> = EchoLyricsSource.entries.toList(),
    val notificationEnabled: Boolean = true,
    val statusOverlayEnabled: Boolean = false,
    val systemStatusBarEnabled: Boolean = false,
    val statusHideTranslation: Boolean = true,
    val statusFontSp: Float = 12f,
    val statusWidthDp: Int = 180,
    val statusOffsetXDp: Int = 80,
    val statusOffsetYDp: Int = 0,
    val bluetoothDelayMs: Int = 0,
    val translationScale: Float = 1f,
    val romanizationScale: Float = 1f,
) {
    val normalized: EchoLyricsOptions
        get() = copy(
            sourceOrder = (sourceOrder + EchoLyricsSource.entries).distinct(),
            statusFontSp = statusFontSp.takeIf { it.isFinite() }?.coerceIn(9f, 18f) ?: 12f,
            statusWidthDp = statusWidthDp.coerceIn(80, 320),
            statusOffsetXDp = statusOffsetXDp.coerceIn(0, 320),
            statusOffsetYDp = statusOffsetYDp.coerceIn(0, 96),
            bluetoothDelayMs = bluetoothDelayMs.coerceIn(0, 2_000),
            translationScale = secondaryScale(translationScale),
            romanizationScale = secondaryScale(romanizationScale),
        )

    companion object {
        fun secondaryScale(value: Float): Float = value.takeIf(Float::isFinite)?.coerceIn(0.5f, 2f) ?: 1f
        fun sourcesFromId(value: String?): List<EchoLyricsSource> =
            (value.orEmpty().split(',').mapNotNull { id -> EchoLyricsSource.entries.find { it.id == id } } +
                EchoLyricsSource.entries).distinct()
    }
}
