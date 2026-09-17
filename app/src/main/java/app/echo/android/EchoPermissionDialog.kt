package app.echo.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoMotion
import app.echo.android.design.EchoThemeTokens
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.echoGlassContainerBrush
import app.echo.android.design.echoGlassRowBrush
import app.echo.android.design.echoPressFeedback
import app.echo.android.design.echoTheme

private val CardShape = RoundedCornerShape(32.dp)
private val RowShape = RoundedCornerShape(20.dp)
private val BadgeShape = RoundedCornerShape(14.dp)
private val PillShape = RoundedCornerShape(999.dp)
private val ButtonShape = RoundedCornerShape(18.dp)

private const val RowStaggerMs = 70
private const val RowEnterMs = 320
private const val HeroBreathMs = 2600

@Composable
fun EchoPermissionDialog(
    visible: Boolean,
    permissionStatuses: List<PermissionEntry>,
    onDismiss: () -> Unit,
    onRequestPermission: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    AnimatedVisibility(
        visible = visible,
        enter = if (lightweight) fadeIn(tween(120)) else EchoMotion.dialogEnter(),
        exit = if (lightweight) fadeOut(tween(90)) else EchoMotion.dialogExit(),
    ) {
        val theme = echoTheme()
        val dark = theme.dark
        val scheme = MaterialTheme.colorScheme

        Box(
            modifier = Modifier
                .fillMaxSize()
                .permissionScrim(theme, scheme.tertiary, lightweight),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .then(
                        if (lightweight) {
                            Modifier
                        } else {
                            Modifier.shadow(
                                elevation = 28.dp,
                                shape = CardShape,
                                ambientColor = theme.accentDeep.copy(alpha = if (dark) 0.55f else 0.30f),
                                spotColor = theme.accentDeep.copy(alpha = if (dark) 0.55f else 0.30f),
                            )
                        },
                    )
                    .clip(CardShape)
                    .background(if (dark) theme.panel.copy(alpha = 0.96f) else theme.surface)
                    .background(echoGlassContainerBrush(strength = 0.9f, accent = theme.accent))
                    .border(echoDarkGlassBorder(), CardShape)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PermissionHero(theme = theme, lightweight = lightweight)

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.permission_welcome).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp,
                    color = scheme.primary,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.heading,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.permission_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.muted,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                Spacer(modifier = Modifier.height(26.dp))

                permissionStatuses.forEachIndexed { index, entry ->
                    StaggeredEntrance(index = index, lightweight = lightweight) {
                        PermissionRow(
                            icon = entry.icon,
                            label = entry.label,
                            description = entry.description,
                            granted = entry.granted,
                            onAction = {
                                if (entry.granted) return@PermissionRow
                                if (entry.canRequest) onRequestPermission(entry.permission)
                                else onOpenSettings()
                            },
                            theme = theme,
                        )
                    }
                    if (index != permissionStatuses.lastIndex) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                ContinueButton(
                    text = stringResource(R.string.permission_continue),
                    theme = theme,
                    onClick = onDismiss,
                )

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.permission_skip),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Layered backdrop: theme wash plus two soft accent glows. Glows are skipped in lightweight mode. */
private fun Modifier.permissionScrim(
    theme: EchoThemeTokens,
    tertiary: Color,
    lightweight: Boolean,
): Modifier = drawWithCache {
    val dark = theme.dark
    val base = Brush.verticalGradient(
        listOf(
            theme.night.copy(alpha = if (dark) 0.94f else 0.88f),
            theme.ink.copy(alpha = if (dark) 0.92f else 0.84f),
            theme.night.copy(alpha = if (dark) 0.96f else 0.90f),
        ),
    )
    val radius = size.maxDimension
    val glowTop = Brush.radialGradient(
        colors = listOf(theme.accent.copy(alpha = if (dark) 0.26f else 0.16f), Color.Transparent),
        center = Offset(size.width * 0.12f, size.height * 0.14f),
        radius = radius * 0.58f,
    )
    val glowBottom = Brush.radialGradient(
        colors = listOf(tertiary.copy(alpha = if (dark) 0.18f else 0.10f), Color.Transparent),
        center = Offset(size.width * 0.92f, size.height * 0.88f),
        radius = radius * 0.52f,
    )
    onDrawBehind {
        drawRect(base)
        if (!lightweight) {
            drawRect(glowTop)
            drawRect(glowBottom)
        }
    }
}

@Composable
private fun PermissionHero(
    theme: EchoThemeTokens,
    lightweight: Boolean,
) {
    val glowScale = if (lightweight) {
        null
    } else {
        rememberInfiniteTransition(label = "permission-hero-glow").animateFloat(
            initialValue = 0.86f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(HeroBreathMs, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "permission-hero-glow-scale",
        )
    }
    val glowColor = theme.accent.copy(alpha = if (theme.dark) 0.22f else 0.16f)
    val staticGlow = theme.accent.copy(alpha = if (theme.dark) 0.16f else 0.12f)

    Box(
        modifier = Modifier
            .size(148.dp)
            .drawBehind {
                // Soft halo under the mascot; breathes gently unless lightweight mode disables it.
                val scale = glowScale?.value ?: 1f
                val color = if (glowScale == null) staticGlow else glowColor
                val radius = size.minDimension * 0.66f * scale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color, Color.Transparent),
                        center = Offset(center.x, center.y + size.height * 0.08f),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(center.x, center.y + size.height * 0.08f),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.echo_startup_still),
            contentDescription = null,
            modifier = Modifier.size(124.dp),
        )
    }
}

/** One-shot fade + rise per row, staggered by index. Disposed with the dialog, so nothing lingers. */
@Composable
private fun StaggeredEntrance(
    index: Int,
    lightweight: Boolean,
    content: @Composable () -> Unit,
) {
    if (lightweight) {
        content()
        return
    }
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    val delay = index * RowStaggerMs
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(RowEnterMs, delayMillis = delay, easing = EchoMotion.Silk)) +
            slideInVertically(tween(RowEnterMs, delayMillis = delay, easing = EchoMotion.Silk)) { it / 3 },
    ) {
        content()
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    label: String,
    description: String,
    granted: Boolean,
    onAction: () -> Unit,
    theme: EchoThemeTokens,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = theme.dark
    val successColor = if (dark) Color(0xFF7EDDAA) else Color(0xFF2E8B57)
    val badgeColor by animateColorAsState(
        targetValue = if (granted) successColor.copy(alpha = 0.16f) else scheme.primary.copy(alpha = if (dark) 0.16f else 0.12f),
        animationSpec = tween(EchoMotion.FadeMs, easing = EchoMotion.Silk),
        label = "permission-badge",
    )
    val iconTint by animateColorAsState(
        targetValue = if (granted) successColor else scheme.primary,
        animationSpec = tween(EchoMotion.FadeMs, easing = EchoMotion.Silk),
        label = "permission-icon",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(echoGlassRowBrush(selected = granted))
            .border(echoDarkGlassBorder(selected = granted), RowShape)
            .padding(start = 14.dp, end = 12.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(BadgeShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = theme.heading,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = theme.muted,
                lineHeight = 16.sp,
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (granted) {
            Surface(
                shape = PillShape,
                color = successColor.copy(alpha = if (dark) 0.16f else 0.12f),
                border = BorderStroke(1.dp, successColor.copy(alpha = 0.32f)),
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = successColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.permission_granted),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = successColor,
                    )
                }
            }
        } else {
            Surface(
                onClick = onAction,
                shape = PillShape,
                color = scheme.primary.copy(alpha = if (dark) 0.18f else 0.12f),
                border = BorderStroke(1.dp, scheme.primary.copy(alpha = if (dark) 0.34f else 0.28f)),
            ) {
                Text(
                    text = stringResource(R.string.permission_allow),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ContinueButton(
    text: String,
    theme: EchoThemeTokens,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val brush = remember(theme) {
        Brush.horizontalGradient(
            listOf(
                theme.accent,
                lerp(theme.accent, theme.accentDeep, if (theme.dark) 0.55f else 0.85f),
            ),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .echoPressFeedback(interactionSource)
            .clip(ButtonShape)
            .background(brush)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = if (theme.dark) 0.18f else 0.24f)), ButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = theme.onAccent,
        )
    }
}

data class PermissionEntry(
    val permission: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val granted: Boolean,
    val canRequest: Boolean,
)
