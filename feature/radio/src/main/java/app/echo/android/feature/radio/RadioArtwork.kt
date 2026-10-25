package app.echo.android.feature.radio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.echo.android.design.*
import app.echo.android.model.radio.*

val LocalOfficialRadioArtwork = staticCompositionLocalOf<String?> { null }

@Composable
internal fun RadioArtwork(station: EchoRadioStation, modifier: Modifier = Modifier, framed: Boolean = true) {
    val url = RadioArtworkPolicy.corrected(station.artworkUrl) ?: LocalOfficialRadioArtwork.current.takeIf {
        station.url.trimEnd('/') == "https://echonext.moe/radio/stream"
    }
    if (url != null) {
        EchoArtworkImage(url, null, modifier, shape = RoundedCornerShape(18.dp), sizeClass = EchoArtworkSize.Card,
            placeholder = { RadioFallback(Modifier.fillMaxSize(), framed) })
    } else {
        RadioFallback(modifier, framed)
    }
}

@Composable
private fun RadioFallback(modifier: Modifier, framed: Boolean) {
        val line = MaterialTheme.colorScheme.onSurfaceVariant
        Box(modifier.clip(RoundedCornerShape(18.dp)).background(if (framed) MaterialTheme.colorScheme.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent), contentAlignment = Alignment.Center) {
            if (framed) Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width * .78f, size.height * .15f)
                repeat(3) { drawCircle(line.copy(alpha = .06f), size.minDimension * (.4f + .22f * it), center, style = Stroke(1.dp.toPx())) }
            }
            EchoIcon(Icons.Rounded.Radio, null, Modifier.fillMaxSize(.4f))
        }
}
