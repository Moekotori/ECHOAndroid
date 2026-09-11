package app.echo.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.echoTheme

@Composable
fun EchoPermissionDialog(
    visible: Boolean,
    permissionStatuses: List<PermissionEntry>,
    onDismiss: () -> Unit,
    onRequestPermission: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = EchoMotion.dialogEnter(),
        exit = EchoMotion.dialogExit(),
    ) {
        val dark = LocalEchoDarkTheme.current
        val scheme = MaterialTheme.colorScheme
        val surfaceColor = if (dark) echoTheme().panel.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.96f)
        val borderColor = if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, echoTheme().glassBorder)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            echoTheme().night.copy(alpha = if (dark) 0.92f else 0.72f),
                            echoTheme().ink.copy(alpha = if (dark) 0.88f else 0.68f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(surfaceColor)
                    .border(borderColor, RoundedCornerShape(28.dp))
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = if (dark) echoTheme().night else scheme.primary,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (dark) echoTheme().ink.copy(alpha = 0.6f) else scheme.primary.copy(alpha = 0.1f))
                        .padding(12.dp),
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.permission_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )

                Spacer(modifier = Modifier.height(24.dp))

                permissionStatuses.forEach { entry ->
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
                        dark = dark,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (dark) scheme.primary.copy(alpha = 0.18f) else scheme.primary,
                        contentColor = if (dark) scheme.primary else scheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.permission_continue),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.permission_skip),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    label: String,
    description: String,
    granted: Boolean,
    onAction: () -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val rowBg = if (dark) echoTheme().ink.copy(alpha = 0.45f) else echoTheme().mist
    val checkColor = if (dark) Color(0xFF6DD4A0) else Color(0xFF2E8B57)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rowBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (granted) checkColor else if (dark) echoTheme().night else scheme.primary,
            modifier = Modifier.size(28.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (granted) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.permission_granted),
                tint = checkColor,
                modifier = Modifier.size(24.dp),
            )
        } else {
            TextButton(onClick = onAction) {
                Text(
                    text = stringResource(R.string.permission_allow),
                    fontWeight = FontWeight.Bold,
                    color = if (dark) scheme.primary else scheme.primary,
                    fontSize = 14.sp,
                )
            }
        }
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
