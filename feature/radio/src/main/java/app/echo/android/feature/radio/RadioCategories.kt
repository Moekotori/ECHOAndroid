package app.echo.android.feature.radio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

private val dialCategories = listOf(
    Triple("music", R.string.radio_category_music, Icons.Rounded.Piano),
    Triple("spoken", R.string.radio_category_spoken, Icons.Rounded.RecordVoiceOver),
    Triple("sports", R.string.radio_category_sports, Icons.Rounded.SportsSoccer),
    Triple("locations", R.string.radio_category_locations, Icons.Rounded.Public),
)

private val spokenGenres = listOf(
    "news" to R.string.radio_category_news,
    "talk" to R.string.radio_category_talk,
    "sports" to R.string.radio_category_sports,
)

private val musicGenres = listOf(
    "pop" to R.string.radio_category_pop,
    "rock" to R.string.radio_category_rock,
    "jazz" to R.string.radio_category_jazz,
    "classical" to R.string.radio_category_classical,
    "electronic" to R.string.radio_category_electronic,
    "easy listening" to R.string.radio_category_easy,
    "folk" to R.string.radio_category_folk,
    "blues" to R.string.radio_category_blues,
    "country" to R.string.radio_category_country,
    "hip hop" to R.string.radio_category_hiphop,
    "j-pop" to R.string.radio_category_jpop,
    "jrock" to R.string.radio_category_jrock,
    "anime" to R.string.radio_category_anime,
    "vocaloid" to R.string.radio_category_vocaloid,
    "touhou" to R.string.radio_category_touhou,
    "doujin" to R.string.radio_category_doujin,
    "vgm" to R.string.radio_category_game,
    "chiptune" to R.string.radio_category_chiptune,
)

@Composable
internal fun RadioCategoryDial(onGroup: (String) -> Unit) {
    val minor = MaterialTheme.colorScheme.outlineVariant
    val major = MaterialTheme.colorScheme.primary
    val ticksPerCategory = 6
    val tickCount = dialCategories.size * ticksPerCategory
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val baseline = size.height - .5.dp.toPx()
            drawLine(minor, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
            for (index in 1 until tickCount) {
                val isCategory = index % ticksPerCategory == ticksPerCategory / 2
                val x = size.width * index / tickCount
                val length = if (isCategory) 10.dp else 3.dp
                drawLine(
                    if (isCategory) major else minor,
                    Offset(x, baseline - length.toPx()),
                    Offset(x, baseline),
                    strokeWidth = (if (isCategory) 2.dp else 1.dp).toPx(),
                )
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            dialCategories.forEach { (tag, label, icon) ->
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { onGroup(tag) }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 2.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    EchoIcon(icon, null, Modifier.size(20.dp))
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RadioGenres(group: String, onTag: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val genres = if (group == "spoken") spokenGenres else musicGenres
        genres.forEach { (tag, label) ->
            TextButton(onClick = { onTag(tag) }) {
                Text(stringResource(label), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
