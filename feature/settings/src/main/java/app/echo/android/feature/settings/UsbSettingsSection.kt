package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoPageSection

/** Soft grouping for USB controls, without changing the other settings pages. */
@Composable
internal fun UsbSettingsSection(
    title: String,
    secondary: Boolean = false,
    contentSpacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsContentInset)
            .echoPageSection().settingsSearchAnchor(title),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp).semantics { heading() },
                style = if (secondary) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (secondary) scheme.onSurfaceVariant else scheme.onSurface,
            )
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(contentSpacing),
                content = content)
        }
    }
}
