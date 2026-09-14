package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.echoClickable
import app.echo.android.design.echoFontFamilyForMode

@Composable
internal fun SettingsAboutIdentity(appVersionLabel: String) {
    val colors = MaterialTheme.colorScheme
    val brandFont = echoFontFamilyForMode("outfit")
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "ECHO", fontFamily = brandFont, fontWeight = FontWeight.Medium,
            fontSize = MaterialTheme.typography.displayLarge.fontSize,
            letterSpacing = (-2).sp, color = colors.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(appVersionLabel, fontFamily = brandFont, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal, color = colors.onSurfaceVariant)
            Surface(color = colors.primary.copy(alpha = 0.08f), shape = RoundedCornerShape(6.dp)) {
                Text("Beta", fontFamily = brandFont, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = colors.primary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_about_author), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal, color = colors.onSurfaceVariant)
            Text("Moekotori", fontFamily = brandFont, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, color = colors.onSurface)
        }
    }
}

@Composable
internal fun SettingsAboutPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = settingsPanelColor()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), content = content)
    }
}

@Composable
internal fun SettingsAboutLinks(openLink: (String) -> Unit) {
    SettingsAboutPanel {
        AboutLinkRow(Icons.Rounded.Forum, stringResource(R.string.settings_about_qq_group), null) {
            openLink("https://qm.qq.com/q/j1qdjGfGKc")
        }
        AboutLinkRow(Icons.Rounded.Language, stringResource(R.string.settings_about_website), "echonext.moe") {
            openLink("https://echonext.moe")
        }
        AboutLinkRow(Icons.Rounded.BugReport, stringResource(R.string.settings_about_bug_report), "GitHub Issues") {
            openLink("https://github.com/moekotori/echoandroid/issues")
        }
    }
}

@Composable
private fun AboutLinkRow(icon: ImageVector, title: String, detail: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().echoClickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 60.dp).padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(21.dp), tint = colors.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal, color = colors.onSurface)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal, color = colors.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ArrowOutward, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
    }
}
