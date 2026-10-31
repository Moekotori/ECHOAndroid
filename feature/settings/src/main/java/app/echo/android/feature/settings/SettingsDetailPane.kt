package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Category scroll state survives resizing through the caller's saveable-state holder. */
@Composable
internal fun SettingsDetailPane(
    category: SettingsCategory,
    subNav: SettingsSubNav,
    compactMode: Boolean,
    searchFocus: SettingsSearchFocus?,
    content: @Composable (SettingsCategory, SettingsSubNav) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 172.dp),
        verticalArrangement = Arrangement.spacedBy(
            if (category == SettingsCategory.Appearance || compactMode) 20.dp else 28.dp,
        )) {
        if (category != SettingsCategory.About && category != SettingsCategory.Appearance &&
            category != SettingsCategory.Library && category != SettingsCategory.Usb) {
            Text(stringResource(category.description), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = SettingsContentInset))
        }
        val subPageTitle = subNav.page?.let { stringResource(it.title) }
        CompositionLocalProvider(
            LocalSettingsSearchFocus provides searchFocus,
            LocalSettingsSubPageTitle provides subPageTitle,
        ) { content(category, subNav) }
    }
}
