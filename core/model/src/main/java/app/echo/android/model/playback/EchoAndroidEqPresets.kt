package app.echo.android.model.playback

import kotlin.math.roundToInt

internal object EchoAndroidEqPresets {
    private const val AutoEqTargetRoot =
        "https://github.com/jaakkopasanen/AutoEq/blob/7ae0f56d53074872b028649617a22bbb4232feb7/targets"
    private val centers = EchoPcEqPresets.frequencies.map { it.roundToInt() }

    val definitions = listOf(
        // The two Harman bass profiles fit half of the difference between the target
        // and its "without bass" CSV to ECHO's fixed filters. AutoEq data is MIT-licensed.
        preset("harman-oe-2018-bass", "Harman Over-Ear 2018 Bass", -3.8f,
            20 to 2.5f, 25 to 2.1f, 32 to 2.1f, 40 to 2.1f, 50 to 2.0f, 63 to 1.9f,
            80 to 1.6f, 100 to 1.1f, 125 to 0.6f, 160 to 0.3f, 200 to 0.1f, 250 to 0.0f,
            sourceUrl = "$AutoEqTargetRoot/Harman%20over-ear%202018.csv",
        ),
        preset("harman-ie-2019-bass", "Harman In-Ear 2019 Bass", -5.5f,
            20 to 3.7f, 25 to 3.2f, 32 to 3.2f, 40 to 3.1f, 50 to 2.9f, 63 to 2.8f,
            80 to 2.4f, 100 to 1.6f, 125 to 1.0f, 160 to 0.4f, 200 to 0.1f, 250 to 0.0f,
            sourceUrl = "$AutoEqTargetRoot/Harman%20in-ear%202019.csv",
        ),
        preset("deep-sub", "Deep Sub-Bass", -4.3f,
            20 to 1.8f, 40 to 2.5f, 63 to 1.8f, 125 to 0.1f, 250 to -0.7f, 630 to 0.0f,
            20000 to 0.0f,
        ),
        preset("tight-bass", "Tighter Bass", -1.3f,
            20 to -1.2f, 63 to -1.8f, 125 to -1.2f, 315 to 0.0f, 1000 to 0.3f, 3150 to 0.5f,
            10000 to 0.0f,
        ),
        preset("clear-mids", "Less Mud", -1.4f,
            20 to 0.0f, 80 to 0.0f, 160 to -0.8f, 315 to -1.8f, 630 to -0.7f, 1000 to 0.0f,
            2500 to 0.6f, 6300 to 0.0f,
        ),
        preset("soft-sibilance", "Softer Sibilance", 0.0f,
            20 to 0.0f, 2500 to 0.0f, 4000 to -0.3f, 6300 to -1.5f, 8000 to -2.2f, 10000 to -1.4f,
            16000 to 0.0f,
        ),
        preset("full-voice", "Fuller Vocals", -2.1f,
            20 to -0.8f, 63 to -0.4f, 160 to 0.0f, 250 to 1.1f, 500 to 0.8f, 1000 to 0.3f,
            2500 to 0.4f, 5000 to 0.0f, 10000 to -0.4f,
        ),
        preset("small-speaker-clear", "Small Speaker Clarity", -2.4f,
            20 to -3.0f, 63 to -2.0f, 125 to -0.9f, 315 to -0.7f, 630 to 0.0f, 2000 to 1.2f,
            3150 to 1.0f, 8000 to 0.0f,
        ),
        preset("dialogue-focus", "Dialogue Focus", -2.9f,
            20 to -2.0f, 80 to -1.6f, 200 to -0.9f, 630 to 0.0f, 1600 to 1.1f, 2500 to 1.6f,
            4000 to 1.0f, 8000 to -0.5f, 20000 to -0.6f,
        ),
        preset("jazz-club", "Jazz Club", -1.8f,
            20 to 0.0f, 80 to 0.7f, 250 to 0.8f, 630 to 0.2f, 1600 to 0.0f, 4000 to -0.3f,
            8000 to 0.6f, 12500 to 0.6f, 20000 to 0.0f,
        ),
        preset("hip-hop-sub", "Hip-Hop Sub", -3.6f,
            20 to 1.8f, 40 to 2.0f, 80 to 1.4f, 160 to 0.2f, 315 to -0.5f, 1000 to 0.0f,
            2500 to 0.4f, 6300 to -0.3f, 12500 to 0.1f,
        ),
        preset("metal-edge", "Metal Focus", -2.1f,
            20 to -0.5f, 63 to 0.6f, 125 to 0.6f, 250 to -0.7f, 500 to -0.3f, 1250 to 0.9f,
            2000 to 1.1f, 4000 to -0.2f, 6300 to -0.7f, 16000 to 0.0f,
        ),
        preset("dance-pulse", "Dance Pulse", -2.8f,
            20 to 0.5f, 63 to 1.5f, 100 to 1.3f, 200 to 0.1f, 400 to -0.5f, 1250 to 0.0f,
            3150 to 0.5f, 8000 to 1.0f, 12500 to 0.6f, 20000 to 0.0f,
        ),
        preset("folk-strings", "Folk Strings", -1.6f,
            20 to -0.7f, 80 to -0.4f, 200 to 0.6f, 400 to 0.7f, 1000 to 0.0f, 2500 to 0.6f,
            5000 to 0.4f, 10000 to -0.3f, 20000 to -0.4f,
        ),
        preset("ambient-space", "Ambient Space", -2.1f,
            20 to 0.9f, 63 to 0.7f, 160 to 0.0f, 500 to -0.4f, 1250 to -0.4f, 4000 to 0.0f,
            10000 to 1.1f, 16000 to 0.7f, 20000 to 0.1f,
        ),
    )

    private fun preset(
        id: String,
        name: String,
        preampDb: Float,
        vararg anchors: Pair<Int, Float>,
        sourceUrl: String? = null,
    ): EchoEqualizerPresetDefinition {
        val indices = anchors.map { (hz, gain) -> centers.indexOf(hz) to gain }
        val gains = centers.indices.map { index ->
            val right = indices.indexOfFirst { (center, _) -> center >= index }
            when (right) {
                -1 -> indices.last().second
                0 -> indices.first().second
                else -> {
                    val (low, lowGain) = indices[right - 1]
                    val (high, highGain) = indices[right]
                    lowGain + (highGain - lowGain) * (index - low) / (high - low)
                }
            }
        }
        return EchoEqualizerPresetDefinition(id, name, gains, preampDb, sourceUrl = sourceUrl)
    }
}
