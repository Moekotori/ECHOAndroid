package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val MaxSpecGroups = 4

/** How many tracks sit at each format and sample rate; only worth drawing when the album mixes them. */
@Composable
internal fun AlbumSpecBar(groups: List<AlbumSpecGroup>, accent: Color) {
    val shown = groups.take(MaxSpecGroups)
    if (shown.size < 2) return
    val colors = rememberDetailGlassColors()
    val swatches = listOf(
        accent,
        lerp(accent, colors.content, 0.45f),
        colors.muted,
        colors.muted.copy(alpha = 0.5f),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(10.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            shown.forEachIndexed { index, group ->
                Box(
                    Modifier
                        .weight(group.trackCount.toFloat())
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(swatches[index]),
                )
            }
        }
        shown.forEachIndexed { index, group ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(swatches[index]))
                Spacer(Modifier.width(9.dp))
                Text(
                    listOfNotNull(group.spec.formatKey, group.spec.sampleRateHz?.let(::formatSampleRate)).joinToString(" · "),
                    modifier = Modifier.weight(1f),
                    color = colors.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    libraryTrackCountLabel(group.trackCount),
                    color = colors.muted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
