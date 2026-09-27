package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoAnimateContentSize
import app.echo.android.design.echoClickable

internal val LocalSettingsCompactMode = staticCompositionLocalOf { false }

@Composable
internal fun SettingsSectionCard(
    title: String,
    collapsible: Boolean = false,
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
    persistentContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().settingsSearchAnchor(title),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (collapsible) Modifier.echoClickable(role = Role.Button) {
                    onExpandedChange(!expanded)
                } else Modifier)
                .heightIn(min = if (collapsible) 48.dp else 28.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariant,
            )
            if (collapsible) Icon(
                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Surface(shape = RoundedCornerShape(20.dp), color = settingsPanelColor()) {
            Column(
                Modifier.fillMaxWidth()
                    .then(if (!LocalEchoEffectivePerformanceMode.current.isLightweight) {
                        Modifier.echoAnimateContentSize()
                    } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(if (LocalSettingsCompactMode.current) 2.dp else 6.dp),
            ) {
                persistentContent()
                if (expanded) content()
            }
        }
    }
}
