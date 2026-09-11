package app.echo.android.feature.player

import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.dsdFamilyLabel
import app.echo.android.model.playback.isDsdDopOutput
import app.echo.android.model.playback.isDsdSource

internal fun playbackFormatChips(
    diagnostics: EchoPlaybackDiagnostics,
    pcmRateLabel: (Int) -> String,
    channelLabel: (Int) -> String = ::defaultChannelLabel,
): List<String> {
    if (diagnostics.isDsdSource()) {
        return listOfNotNull(
            diagnostics.dsdFamilyLabel() ?: diagnostics.codec ?: "DSD",
            diagnostics.decodedSampleRateHz?.takeIf { it > 0 }?.let { rate ->
                val kind = if (diagnostics.isDsdDopOutput()) "DoP" else "PCM"
                "${pcmRateLabel(rate)} $kind"
            },
            diagnostics.channelCount?.takeIf { it > 0 }?.let(channelLabel),
        )
    }
    return listOfNotNull(
        diagnostics.codec,
        diagnostics.sampleRateHz?.takeIf { it > 0 }?.let(pcmRateLabel),
        diagnostics.bitDepth?.takeIf { it > 0 }?.let { "${it}bit" },
        diagnostics.channelCount?.takeIf { it > 0 }?.let(channelLabel),
    )
}

private fun defaultChannelLabel(channels: Int): String = when (channels) {
    1 -> "Mono"
    2 -> "2CH"
    else -> "${channels}CH"
}
