package app.echo.android.feature.radio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.radio.RadioDirectoryQuery

@Composable
internal fun RadioDirectorySearch(state: RadioDiscoveryState, onSearch: (RadioDirectoryQuery) -> Unit) {
    var text by rememberSaveable(state.query.name) { mutableStateOf(state.query.name) }
    Row {
        OutlinedTextField(text, { text = it.take(80) }, Modifier.weight(1f), singleLine = true,
            label = { Text(stringResource(R.string.radio_search_global)) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch(state.query.copy(name = text, page = 0)) }))
        TextButton(onClick = { onSearch(state.query.copy(name = text, page = 0)) }) { Text(stringResource(R.string.radio_search)) }
    }
    if (state.query.tag.isNotBlank() || state.query.countryCode.isNotBlank() || state.query.language.isNotBlank()) {
        Text(listOf(state.query.tag, state.query.countryCode, state.query.language).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun RadioFilterDialog(query: RadioDirectoryQuery, onApply: (RadioDirectoryQuery) -> Unit, onDismiss: () -> Unit) {
    var country by rememberSaveable { mutableStateOf(query.countryCode) }
    var language by rememberSaveable { mutableStateOf(query.language) }
    var tag by rememberSaveable { mutableStateOf(query.tag) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.radio_filters)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(country, { country = it.uppercase().take(2) }, label = { Text(stringResource(R.string.radio_country)) }, singleLine = true,
                isError = country.isNotBlank() && !country.matches(Regex("[A-Z]{2}")))
            OutlinedTextField(language, { language = it.take(80) }, label = { Text(stringResource(R.string.radio_language)) }, singleLine = true)
            OutlinedTextField(tag, { tag = it.take(80) }, label = { Text(stringResource(R.string.radio_tag)) }, singleLine = true)
            TextButton(onClick = { country = ""; language = ""; tag = "" }) { Text(stringResource(R.string.radio_reset)) }
        }
    }, confirmButton = { TextButton(enabled = country.isBlank() || country.matches(Regex("[A-Z]{2}")),
        onClick = { onApply(query.copy(countryCode = country, language = language, tag = tag, page = 0)) }) { Text(stringResource(R.string.radio_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.radio_cancel)) } })
}

@OptIn(ExperimentalLayoutApi::class)
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
    FlowRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { (tag, label) ->
            OutlinedButton(onClick = { if (group == "home") onGroup(tag) else onTag(tag) }) { Text(stringResource(label)) }
        }
    }
}
