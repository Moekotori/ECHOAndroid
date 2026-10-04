package app.echo.android.model.library

/** Indexed source metadata only; never a claim about decoded or device output quality. */
data class LibraryAudioProfile(
    val trackCount: Int = 0,
    val losslessCount: Int = 0,
    val lossyCount: Int = 0,
    val dsdCount: Int = 0,
    val unconfirmedCount: Int = 0,
    val highSampleRateCount: Int = 0,
    val formats: List<LibraryAudioDistribution> = emptyList(),
    val sampleRates: List<LibraryAudioDistribution> = emptyList(),
)

/** Format codes or sampling rates in Hz; OTHER and UNKNOWN have explicit UI labels. */
data class LibraryAudioDistribution(val key: String, val count: Int)
