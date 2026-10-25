package app.echo.android.feature.radio

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import java.util.Locale

@Composable
internal fun RadioRegionOptions(country: String, language: String, onCountry: (String) -> Unit, onLanguage: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val countries = remember(locale) {
        "CN TW HK MO JP KR SG MY TH VN IN ID PH GB FR DE IT ES NL CH AT BE DK FI NO SE PL CZ GR UA RU TR US CA MX BR AR CL CO AU NZ ZA EG MA NG KE"
            .split(' ').map { it to Locale.Builder().setRegion(it).build().getDisplayCountry(locale) }
    }
    val languages = remember(locale) {
        listOf("zh" to "chinese", "en" to "english", "ja" to "japanese", "ko" to "korean", "de" to "german", "fr" to "french",
            "es" to "spanish", "pt" to "portuguese", "it" to "italian", "ru" to "russian", "ar" to "arabic", "hi" to "hindi", "nl" to "dutch", "tr" to "turkish")
            .map { (code, value) -> value to Locale.forLanguageTag(code).getDisplayLanguage(locale) }
    }
    RadioOptionPicker(stringResource(R.string.radio_choose_country), stringResource(R.string.radio_any_country), country, countries, onCountry)
    RadioOptionPicker(stringResource(R.string.radio_choose_language), stringResource(R.string.radio_any_language), language, languages, onLanguage)
}

@Composable
private fun RadioOptionPicker(label: String, any: String, value: String, options: List<Pair<String, String>>, onValue: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Text(if (value.isBlank()) any else options.firstOrNull { it.first == value }?.second ?: value, Modifier.weight(1f))
            EchoIcon(Icons.Rounded.KeyboardArrowDown, null)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            DropdownMenuItem(text = { Text(any) }, onClick = { onValue(""); expanded = false })
            options.forEach { (code, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onValue(code); expanded = false }) }
        }
    }
}
