package app.echo.android.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared outline of every bottom sheet in the app. */
val EchoSheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

/** Surface of a sheet: one fill and a single static accent wash at the top edge, no nested cards. */
@Composable
fun Modifier.echoSheetSurface(): Modifier {
    val dark = LocalEchoDarkTheme.current
    val glowTop = echoAccentColor().copy(alpha = if (dark) 0.16f else 0.11f)
    return clip(EchoSheetShape)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .drawBehind {
            drawRect(Brush.verticalGradient(listOf(glowTop, Color.Transparent), endY = 220.dp.toPx()))
        }
}

/**
 * Drop-in replacement for [ModalBottomSheet] that carries the shared look. Content is a column
 * below a slim grabber; callers keep passing their own state and insets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetGesturesEnabled: Boolean = true,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        shape = EchoSheetShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        scrimColor = BottomSheetDefaults.ScrimColor,
        contentWindowInsets = contentWindowInsets,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().echoSheetSurface()) {
            EchoSheetGrabber()
            content()
        }
    }
}

@Composable
private fun EchoSheetGrabber() {
    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(36.dp, 4.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)),
        )
    }
}

/** Title block at the top of a sheet. [trailing] sits at the end (actions, count, close). */
@Composable
fun EchoSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) Text(
                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}

/** Soft round plate behind a row icon. [active] tints it with the accent; [danger] with the error color. */
@Composable
fun EchoIconPlate(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val dark = LocalEchoDarkTheme.current
    val accent = echoAccentColor()
    val error = MaterialTheme.colorScheme.error
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.09f else 0.07f)
    val plate by animateColorAsState(
        targetValue = when {
            danger -> error.copy(alpha = if (dark) 0.20f else 0.12f)
            active -> accent.copy(alpha = if (dark) 0.30f else 0.20f)
            else -> idle
        },
        animationSpec = tween(if (lightweight) 0 else 240, easing = EchoMotion.Silk),
        label = "echo-icon-plate",
    )
    val tint = (if (danger) error else MaterialTheme.colorScheme.onSurface).copy(alpha = if (enabled) 1f else 0.38f)
    Box(modifier.size(38.dp).clip(CircleShape).background(plate), contentAlignment = Alignment.Center) {
        EchoIcon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/**
 * One tappable sheet row: icon plate, label, optional supporting line and trailing slot.
 * Without [trailing] a chevron is shown, so rows read as destinations.
 */
@Composable
fun EchoSheetRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    chevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.42f
    val labelColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(18.dp))
            .echoClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EchoIconPlate(icon, danger = danger, enabled = enabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = labelColor.copy(alpha = alpha),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
            )
            if (!supporting.isNullOrBlank()) Text(
                supporting, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        when {
            trailing != null -> trailing()
            chevron && enabled && !danger -> EchoIcon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
            )
        }
    }
}
