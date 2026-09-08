package app.echo.android.model.playback

data class OpraEqBand(
    val type: String,
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float?,
    val slope: Float?,
)

data class OpraHeadphoneCorrectionPreset(
    val eqId: String,
    val productId: String,
    val productName: String,
    val vendorName: String,
    val author: String,
    val details: String?,
    val sourceUrl: String?,
    val preampDb: Float,
    val bands: List<OpraEqBand>,
) {
    val displayName: String
        get() = listOf(vendorName, productName, author)
            .filter { it.isNotBlank() }
            .joinToString(" / ")
}

data class OpraHeadphoneCorrectionProduct(
    val productId: String,
    val productName: String,
    val vendorName: String,
    val subtype: String?,
    val presets: List<OpraHeadphoneCorrectionPreset>,
)

data class OpraDatabaseStatus(
    val source: String = "empty",
    val vendorCount: Int = 0,
    val productCount: Int = 0,
    val eqCount: Int = 0,
)

data class OpraHeadphoneCorrectionState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<OpraHeadphoneCorrectionProduct> = emptyList(),
    val status: OpraDatabaseStatus = OpraDatabaseStatus(),
    val selectedEqId: String? = null,
    val message: String? = null,
    val previewCurve: List<EchoEqResponsePoint> = emptyList(),
) {
    val selectedPreset: OpraHeadphoneCorrectionPreset?
        get() = results
            .asSequence()
            .flatMap { it.presets.asSequence() }
            .firstOrNull { it.eqId == selectedEqId }
}
