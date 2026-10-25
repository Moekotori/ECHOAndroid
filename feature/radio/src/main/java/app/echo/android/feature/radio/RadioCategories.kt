package app.echo.android.feature.radio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

@Composable
internal fun RadioCategories(group: String, onGroup: (String) -> Unit, onTag: (String) -> Unit) {
    val categories = when (group) {
        "home" -> listOf("music" to R.string.radio_category_music, "spoken" to R.string.radio_category_spoken,
            "sports" to R.string.radio_category_sports, "locations" to R.string.radio_category_locations)
        "spoken" -> listOf("news" to R.string.radio_category_news, "talk" to R.string.radio_category_talk, "sports" to R.string.radio_category_sports)
        else -> listOf("pop" to R.string.radio_category_pop, "rock" to R.string.radio_category_rock,
            "jazz" to R.string.radio_category_jazz, "classical" to R.string.radio_category_classical,
            "electronic" to R.string.radio_category_electronic, "easy listening" to R.string.radio_category_easy,
            "folk" to R.string.radio_category_folk, "blues" to R.string.radio_category_blues,
            "country" to R.string.radio_category_country, "hip hop" to R.string.radio_category_hiphop,
            "j-pop" to R.string.radio_category_jpop, "jrock" to R.string.radio_category_jrock,
            "anime" to R.string.radio_category_anime, "vocaloid" to R.string.radio_category_vocaloid,
            "touhou" to R.string.radio_category_touhou, "doujin" to R.string.radio_category_doujin,
            "vgm" to R.string.radio_category_game, "chiptune" to R.string.radio_category_chiptune)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (tag, label) ->
                    val icon = when (tag) {
                        "locations" -> Icons.Rounded.Public
                        "spoken", "talk", "news" -> Icons.Rounded.RecordVoiceOver
                        "sports" -> Icons.Rounded.SportsSoccer
                        "music", "classical" -> Icons.Rounded.Piano
                        "electronic", "chiptune", "vocaloid" -> Icons.Rounded.GraphicEq
                        else -> Icons.Rounded.Headphones
                    }
                    val decoration = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .07f)
                    Card(onClick = { if (group == "home") onGroup(tag) else onTag(tag) }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Box(Modifier.fillMaxWidth().height(64.dp)) {
                            Canvas(Modifier.fillMaxSize()) {
                                repeat(4) { drawCircle(decoration, size.height * (.3f + it * .18f), Offset(size.width * .95f, size.height * .12f), style = Stroke(1.5.dp.toPx())) }
                            }
                            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                EchoIcon(icon, null, Modifier.size(22.dp))
                                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                if (row.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}
