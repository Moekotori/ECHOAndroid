package app.echo.android.model.playback

/** Calibration is independent of personal EQ, its strength/tilt and its saved preset ID. */
data class EchoHeadphoneCorrectionSettings(
    val enabled: Boolean = false,
    val id: String? = null,
    val label: String? = null,
    val preampDb: Float = 0f,
    val filters: List<OpraEqBand> = emptyList(),
) {
    companion object {
        fun fromPreset(preset: OpraHeadphoneCorrectionPreset) = EchoHeadphoneCorrectionSettings(true, preset.eqId, preset.displayName, preset.preampDb, preset.bands)
        fun fromFavorite(preset: EchoEqualizerUserPreset) = EchoHeadphoneCorrectionSettings(true, preset.opraEqId, preset.sourceLabel ?: preset.name, preset.preampDb, preset.filters)
    }
}
