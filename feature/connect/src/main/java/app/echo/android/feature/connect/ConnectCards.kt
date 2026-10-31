package app.echo.android.feature.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable

internal val ConnectCardShape = RoundedCornerShape(20.dp)
internal val ListCardPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)

@Composable
internal fun ConnectCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    spacing: Dp = 12.dp,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ConnectCardShape,
        color = if (highlighted) scheme.primaryContainer.copy(alpha = 0.32f) else scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/** Titled group of content. [list] cards hold divider-separated rows instead of free-form content. */
@Composable
internal fun ConnectSection(
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    list: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) ConnectNote(subtitle)
            }
            action?.invoke()
        }
        if (list) ConnectCard(contentPadding = ListCardPadding, spacing = 0.dp, content = content)
        else ConnectCard(content = content)
    }
}

@Composable
internal fun ConnectRowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
internal fun ConnectIconTile(icon: ImageVector, size: Dp = 44.dp) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            EchoIcon(icon, null, Modifier.size(size * 0.5f))
        }
    }
}

internal enum class ConnectTone { Neutral, Positive, Warning, Error }

@Composable
internal fun ConnectStatusChip(text: String, tone: ConnectTone, progress: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val dot: Color = when (tone) {
        ConnectTone.Positive -> scheme.primary
        ConnectTone.Warning -> scheme.tertiary
        ConnectTone.Error -> scheme.error
        ConnectTone.Neutral -> scheme.outline
    }
    Surface(shape = CircleShape, color = dot.copy(alpha = 0.14f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (progress) CircularProgressIndicator(Modifier.size(10.dp), color = dot, strokeWidth = 1.5.dp)
            else Box(Modifier.size(7.dp).background(dot, CircleShape))
            Text(text, style = MaterialTheme.typography.labelMedium,
                color = if (tone == ConnectTone.Error) scheme.error else scheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Tappable list entry: leading icon, title, optional detail and a chevron. */
@Composable
internal fun ConnectActionRow(icon: ImageVector, title: String, detail: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).echoClickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ConnectIconTile(icon, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrBlank()) ConnectNote(detail)
        }
        EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal class ConnectAction(val icon: ImageVector, val title: String, val detail: String?, val onClick: () -> Unit)

@Composable
internal fun ConnectActionList(actions: List<ConnectAction>) {
    if (actions.isEmpty()) return
    ConnectCard(contentPadding = ListCardPadding, spacing = 0.dp) {
        actions.forEachIndexed { index, action ->
            if (index > 0) ConnectRowDivider()
            ConnectActionRow(action.icon, action.title, action.detail, action.onClick)
        }
    }
}
