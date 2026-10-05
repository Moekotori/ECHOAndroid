package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.PageChrome
import app.echo.android.design.EchoLayoutBounds
import app.echo.android.design.LocalEchoLayout

@Composable
internal fun SettingsWideNavigation(
    category: SettingsCategory?,
    home: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    val navigationWidth = (LocalEchoLayout.current.widthDp * 0.32f).dp.coerceIn(260.dp, 340.dp)
    Row(Modifier.fillMaxSize().imePadding()) {
        EchoLayoutBounds(Modifier.width(navigationWidth).fillMaxHeight()) {
            PageChrome(title = stringResource(R.string.settings_title), subtitle = null, compactHeader = true, badgeContent = {}) { home() }
        }
        VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
        EchoLayoutBounds(Modifier.weight(1f).fillMaxHeight()) {
            PageChrome(title = stringResource(category?.title ?: R.string.settings_title), subtitle = null,
                compactHeader = true, badgeContent = {}) {
                if (category != null) detail() else Text(stringResource(R.string.settings_select_category), Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
