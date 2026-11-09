package app.echo.android.feature.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable

/** A heading followed by either divider-separated rows or free-form content. */
@Composable
internal fun ConnectSection(
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    plain: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!subtitle.isNullOrBlank()) ConnectNote(subtitle)
            }
            action?.invoke()
        }
        if (plain) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        else ConnectFlatGroup(content)
    }
}

/** Card-less group: rows sit directly on the page and are separated by hairlines. */
@Composable
internal fun ConnectFlatGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        ConnectRowDivider()
        Column(Modifier.fillMaxWidth(), content = content)
        ConnectRowDivider()
    }
}

@Composable
internal fun ConnectRowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

internal enum class ConnectTone { Neutral, Positive, Warning, Error }

@Composable
internal fun ConnectStatus(text: String, tone: ConnectTone, progress: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val dot: Color = when (tone) {
        ConnectTone.Positive -> scheme.primary
        ConnectTone.Warning -> scheme.tertiary
        ConnectTone.Error -> scheme.error
        ConnectTone.Neutral -> scheme.outline
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) {
            if (progress) CircularProgressIndicator(Modifier.size(10.dp), color = dot, strokeWidth = 1.5.dp)
            else Box(Modifier.size(6.dp).background(dot, CircleShape))
        }
        Text(text, style = MaterialTheme.typography.labelMedium,
            color = if (tone == ConnectTone.Error) scheme.error else scheme.onSurfaceVariant)
    }
}

/** Tappable list entry: leading icon, title, optional detail and a chevron. */
@Composable
internal fun ConnectActionRow(action: ConnectAction) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = if (action.primary) 60.dp else 48.dp)
        .echoClickable(role = Role.Button, onClick = action.onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EchoIcon(action.icon, null, Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                action.title,
                style = if (action.primary) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                color = if (action.primary) scheme.primary else scheme.onSurface,
            )
            if (!action.detail.isNullOrBlank()) ConnectNote(action.detail)
        }
        EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp),
            tint = scheme.onSurfaceVariant)
    }
}

internal class ConnectAction(
    val icon: ImageVector,
    val title: String,
    val detail: String?,
    val primary: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
internal fun ConnectActionList(actions: List<ConnectAction>, title: String? = null) {
    if (actions.isEmpty()) return
    val rows: @Composable ColumnScope.() -> Unit = {
        actions.forEachIndexed { index, action ->
            if (index > 0) ConnectRowDivider()
            ConnectActionRow(action)
        }
    }
    if (title == null) ConnectFlatGroup(rows)
    else ConnectSection(title, content = rows)
}
