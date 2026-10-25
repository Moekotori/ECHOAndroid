package app.echo.android.feature.radio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var manual by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.radio_filters)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioRegionOptions(country, language, onCountry = { country = it }, onLanguage = { language = it })
            TextButton(onClick = { manual = !manual }) { Text(stringResource(R.string.radio_manual_filters)) }
            if (manual) {
            OutlinedTextField(country, { country = it.uppercase().take(2) }, label = { Text(stringResource(R.string.radio_country)) }, singleLine = true,
                isError = country.isNotBlank() && !country.matches(Regex("[A-Z]{2}")))
            OutlinedTextField(language, { language = it.take(80) }, label = { Text(stringResource(R.string.radio_language)) }, singleLine = true)
            }
            OutlinedTextField(tag, { tag = it.take(80) }, label = { Text(stringResource(R.string.radio_tag)) }, singleLine = true)
            TextButton(onClick = { country = ""; language = ""; tag = "" }) { Text(stringResource(R.string.radio_reset)) }
        }
    }, confirmButton = { TextButton(enabled = country.isBlank() || country.matches(Regex("[A-Z]{2}")),
        onClick = { onApply(query.copy(countryCode = country, language = language, tag = tag, page = 0)) }) { Text(stringResource(R.string.radio_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.radio_cancel)) } })
}
