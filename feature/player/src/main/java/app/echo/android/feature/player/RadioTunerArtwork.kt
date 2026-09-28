package app.echo.android.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** A decorative FM scale, not a seek/tuning control or the stream's actual frequency. */
@Composable
internal fun RadioTuningScale(colors: RadioPlayerColors, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(60.dp)) {
        Canvas(Modifier.fillMaxWidth().height(43.dp).padding(horizontal = 5.dp)) {
            val middle = size.height / 2f
            for (tick in 0..40) {
                val x = size.width * tick / 40f
                val halfHeight = when {
                    tick % 8 == 0 -> size.height * 0.45f
                    tick % 4 == 0 -> size.height * 0.26f
                    else -> size.height * 0.14f
                }
                drawLine(colors.accent.copy(alpha = if (tick % 8 == 0) 0.92f else 0.52f),
                    Offset(x, middle - halfHeight), Offset(x, middle + halfHeight), 0.75.dp.toPx())
            }
            val pointer = size.width * ((98.6f - 88f) / 20f)
            drawLine(colors.accent, Offset(pointer, 0f), Offset(pointer, size.height),
                2.dp.toPx(), StrokeCap.Round)
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().align(Alignment.BottomStart)) {
            for (mark in 0..5) {
                Text((88 + mark * 4).toString(), color = colors.muted,
                    fontFamily = RecordSleeveStyle.BodyFont, fontSize = 11.sp, lineHeight = 14.sp,
                    modifier = Modifier.offset(x = (maxWidth - 20.dp) * mark / 5f))
            }
        }
    }
}

@Composable
internal fun RadioFrequencyDisplay(colors: RadioPlayerColors, frequencyWidth: Dp) {
    // The fixed lining numerals / FM label have no descenders. Trim their font's unused
    // descender space so the following divider is positioned from the visible baseline.
    Layout(content = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(stringResource(R.string.radio_demo_frequency),
                style = TextStyle(color = colors.accent, fontFamily = RecordSleeveStyle.TitleFont,
                    fontFeatureSettings = "lnum", lineHeight = 1.em, letterSpacing = (-2).sp),
                autoSize = TextAutoSize.StepBased(minFontSize = 48.sp, maxFontSize = 100.sp, stepSize = 1.sp),
                maxLines = 1, modifier = Modifier.widthIn(max = frequencyWidth).alignByBaseline())
            Text(stringResource(R.string.radio_demo_band), color = colors.accent,
                fontFamily = RecordSleeveStyle.TitleFont, fontSize = 20.sp, lineHeight = 24.sp,
                modifier = Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.radio_demo_stereo), color = colors.muted,
                fontFamily = RecordSleeveStyle.BodyFont, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 3.sp,
                modifier = Modifier.alignByBaseline())
        }
    }) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints)
        val baseline = placeable[FirstBaseline]
        val height = if (baseline == AlignmentLine.Unspecified) placeable.height
            else (baseline + 6.dp.roundToPx()).coerceIn(constraints.minHeight, placeable.height)
        layout(placeable.width, height) { placeable.placeRelative(0, 0) }
    }
}

// Fixed artwork. No audio readback, random sampling, frame timer or animation loop.
private val RadioWaveHeights = floatArrayOf(
    .03f, .04f, .07f, .04f, .08f, .16f, .10f, .26f, .16f, .10f, .32f,
    .25f, .48f, .26f, .37f, .68f, .44f, .28f, .57f, .40f, .52f, .92f,
    .77f, 1f, .86f, .62f, .45f, .26f, .50f, .38f, .24f, .40f, .29f,
    .21f, .16f, .28f, .18f, .14f, .08f, .10f, .06f, .04f, .03f,
)

@Composable
internal fun RadioDecorativeWaveform(colors: RadioPlayerColors, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(54.dp)) {
        val middle = size.height / 2f
        val inset = 3.dp.toPx()
        for (index in RadioWaveHeights.indices) {
            val x = inset + (size.width - inset * 2f) * index / (RadioWaveHeights.size - 1)
            val halfHeight = RadioWaveHeights[index] * size.height * 0.47f
            drawLine(colors.accent.copy(alpha = 0.82f), Offset(x, middle - halfHeight),
                Offset(x, middle + halfHeight), 1.dp.toPx(), StrokeCap.Round)
        }
    }
}
