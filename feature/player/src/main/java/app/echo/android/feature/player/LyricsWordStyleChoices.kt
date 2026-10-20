package app.echo.android.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoAccentColor
import app.echo.android.model.settings.EchoLyricsWordHighlightStyle
import app.echo.android.model.settings.EchoLyricsWordHighlightStyle as Style

/** The 14 ways a sung word can light up, each shown as a small still of the effect. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LyricsWordStyleChoices(
    selected: EchoLyricsWordHighlightStyle,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        Style.entries.forEach { style ->
            WordStyleCard(style, style == selected, { onSelect(style.id) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WordStyleCard(style: Style, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val dark = LocalEchoDarkTheme.current
    val accent = echoAccentColor()
    val duration = if (LocalEchoEffectivePerformanceMode.current.isLightweight) 0 else 160
    val ink = MaterialTheme.colorScheme.onSurface
    val border by animateColorAsState(
        if (selected) accent else ink.copy(alpha = 0.08f), tween(duration), label = "word-style-border",
    )
    val fill by animateColorAsState(
        if (selected) accent.copy(alpha = if (dark) 0.16f else 0.10f) else ink.copy(alpha = 0.045f),
        tween(duration), label = "word-style-fill",
    )
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(fill).border(BorderStroke(if (selected) 1.5.dp else 1.dp, border), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WordStyleSample(style, lit = if (dark) Color.White else accent, dim = ink.copy(alpha = 0.34f), accent = accent)
        Text(
            stringResource(lyricsWordStyleLabel(style)),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) accent else ink.copy(alpha = 0.82f),
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One lit letter, one mid-word letter showing the effect, one still waiting. Drawn once, never animated. */
@Composable
private fun WordStyleSample(style: Style, lit: Color, dim: Color, accent: Color) {
    // [lit amount, dy, scale] for the three letters.
    val glyphs = when (style) {
        Style.Sweep, Style.Scan, Style.Dots -> listOf(Triple(1f, 0f, 1f), Triple(0f, 0f, 1f), Triple(0f, 0f, 1f))
        Style.Fade, Style.Halo, Style.Spark, Style.Underline -> listOf(Triple(1f, 0f, 1f), Triple(0.62f, 0f, 1f), Triple(0f, 0f, 1f))
        Style.Lift -> listOf(Triple(1f, 0f, 1f), Triple(0.5f, 3f, 1f), Triple(0f, 6f, 1f))
        Style.Hop -> listOf(Triple(1f, 0f, 1f), Triple(0.62f, -6f, 1f), Triple(0f, 0f, 1f))
        Style.Swell -> listOf(Triple(1f, 0f, 1f), Triple(0.62f, 0f, 1.32f), Triple(0f, 0f, 1f))
        Style.Spot -> listOf(Triple(0.7f, 0f, 1f), Triple(1f, 0f, 1f), Triple(0.45f, 0f, 1f))
        Style.Wave -> listOf(Triple(0.8f, -3f, 1f), Triple(1f, -7f, 1f), Triple(0.5f, -2f, 1f))
        Style.Lens -> listOf(Triple(0.8f, 0f, 1.1f), Triple(1f, 0f, 1.32f), Triple(0.5f, 0f, 1.08f))
        Style.Flip -> listOf(Triple(1f, 0f, 1f), Triple(0.62f, 0f, 1f), Triple(0f, 0f, 1f))
    }
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            "Abc".forEachIndexed { index, letter ->
                val (amount, dy, scale) = glyphs[index]
                val color = lerp(dim, lit, amount)
                val mid = index == 1
                val glow = style == Style.Halo && mid
                Box(
                    Modifier.offset(y = dy.dp).graphicsLayer {
                        scaleX = scale
                        scaleY = scale * if (style == Style.Flip && mid) 0.66f else 1f
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    SampleLetter(letter.toString(), color, if (glow) Shadow(accent.copy(alpha = 0.9f), blurRadius = 14f) else null)
                    // Partial wipe on the mid letter: a soft edge for sweep, a hard edge plus line for scan.
                    if (mid && (style == Style.Sweep || style == Style.Scan)) {
                        SampleLetter(letter.toString(), lit, null, Modifier.drawWithContent {
                            clipRect(right = size.width * 0.5f) { this@drawWithContent.drawContent() }
                            if (style == Style.Scan) drawRect(accent, Offset(size.width * 0.5f - 1.dp.toPx(), 2.dp.toPx()),
                                Size(2.dp.toPx(), size.height - 4.dp.toPx()))
                        })
                    }
                    if (mid && style == Style.Dots) {
                        SampleLetter(letter.toString(), lit, null, Modifier.drawWithContent {
                            val dots = Path()
                            val cell = 4.dp.toPx()
                            var y = 0f
                            while (y < size.height) {
                                var x = 0f
                                while (x < size.width) {
                                    dots.addOval(androidx.compose.ui.geometry.Rect(Offset(x + cell / 2f, y + cell / 2f), cell * 0.42f))
                                    x += cell
                                }
                                y += cell
                            }
                            clipPath(dots) { this@drawWithContent.drawContent() }
                        })
                    }
                    if (mid && style == Style.Spark) Text("✦", color = accent, fontSize = 9.sp,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-1).dp))
                    if (style == Style.Underline && index < 2) Box(
                        Modifier.align(Alignment.BottomStart).offset(y = 3.dp)
                            .size(if (index == 0) 16.dp else 8.dp, 2.dp)
                            .background(accent, RoundedCornerShape(1.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleLetter(text: String, color: Color, shadow: Shadow?, modifier: Modifier = Modifier) {
    Text(
        text, color = color, modifier = modifier,
        style = MaterialTheme.typography.titleLarge.copy(shadow = shadow),
        fontSize = 24.sp, fontWeight = FontWeight.Bold,
    )
}

private fun lyricsWordStyleLabel(style: Style): Int = when (style) {
    Style.Sweep -> R.string.lyrics_word_style_sweep
    Style.Fade -> R.string.lyrics_word_style_fade
    Style.Lift -> R.string.lyrics_word_style_lift
    Style.Hop -> R.string.lyrics_word_style_hop
    Style.Swell -> R.string.lyrics_word_style_swell
    Style.Halo -> R.string.lyrics_word_style_halo
    Style.Spot -> R.string.lyrics_word_style_spot
    Style.Wave -> R.string.lyrics_word_style_wave
    Style.Lens -> R.string.lyrics_word_style_lens
    Style.Flip -> R.string.lyrics_word_style_flip
    Style.Spark -> R.string.lyrics_word_style_spark
    Style.Scan -> R.string.lyrics_word_style_scan
    Style.Underline -> R.string.lyrics_word_style_underline
    Style.Dots -> R.string.lyrics_word_style_dots
}
