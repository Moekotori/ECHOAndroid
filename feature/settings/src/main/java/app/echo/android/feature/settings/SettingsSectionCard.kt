package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoPageSection

internal val LocalSettingsCompactMode = staticCompositionLocalOf { false }

@Composable
internal fun SettingsSectionCard(
    title: String,
    secondary: Boolean = false,
    persistentContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().echoPageSection().settingsSearchAnchor(title),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = SettingsContentInset).semantics { heading() },
            style = if (secondary) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (secondary) scheme.onSurfaceVariant else scheme.onSurface,
        )
        HorizontalDivider(
            Modifier.padding(horizontal = SettingsContentInset),
            color = scheme.outlineVariant.copy(alpha = 0.5f),
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = SettingsContentInset, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            persistentContent()
            content()
        }
    }
}
