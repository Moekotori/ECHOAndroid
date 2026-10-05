package app.echo.android.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

/** Static vector illustrations: no bitmaps, decoding, timers or ambient animation. */
@Composable
internal fun FirstLaunchIllustration(step: Int) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().height(156.dp), shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainer) {
        when (step) {
            0 -> Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(132.dp)) {
                    val radius = size.minDimension / 2
                    drawCircle(colors.onSurface.copy(alpha = 0.08f), radius)
                    for (ring in 2..6) {
                        drawCircle(colors.onSurface.copy(alpha = 0.13f), radius * ring / 7,
                            style = Stroke(1.dp.toPx()))
                    }
                    drawCircle(colors.surface, radius * 0.24f)
                    drawCircle(colors.onSurface, 3.dp.toPx())
                    drawLine(colors.onSurface.copy(alpha = 0.4f), Offset(radius * 1.8f, radius * 0.15f),
                        Offset(radius * 1.55f, radius * 0.8f), strokeWidth = 3.dp.toPx())
                }
            }
            1 -> Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.size(56.dp).background(colors.surface, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center) {
                    EchoIcon(Icons.Rounded.PlayArrow, null, Modifier.size(28.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.onboarding_lyrics_preview),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.onboarding_player_preview),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            else -> Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly) {
                EchoIcon(Icons.Rounded.Smartphone, null, Modifier.size(44.dp))
                EchoIcon(Icons.Rounded.Wifi, null, Modifier.size(24.dp))
                EchoIcon(Icons.Rounded.Laptop, null, Modifier.size(64.dp))
            }
        }
    }
}
