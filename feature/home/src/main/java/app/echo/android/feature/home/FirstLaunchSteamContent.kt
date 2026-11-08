package app.echo.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

private const val STEAM_URL = "https://store.steampowered.com/app/5105090/ECHO/"

/** PC edition promotion. The link only opens when the user taps it; nothing is fetched here. */
@Composable
internal fun FirstLaunchSteamContent(onOpenLink: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(colors.primaryContainer, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.onboarding_steam_tag), style = MaterialTheme.typography.labelSmall,
            color = colors.onPrimaryContainer.copy(alpha = 0.85f))
        Text(stringResource(R.string.onboarding_steam_headline), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
        Text(stringResource(R.string.onboarding_steam_body), style = MaterialTheme.typography.bodyMedium,
            color = colors.onPrimaryContainer.copy(alpha = 0.92f))
        Button(
            onClick = { onOpenLink(STEAM_URL) },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.onPrimaryContainer,
                contentColor = colors.primaryContainer,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.onboarding_steam_button))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FirstLaunchBullet(R.string.onboarding_steam_browse)
        FirstLaunchBullet(R.string.onboarding_steam_remote)
        FirstLaunchBullet(R.string.onboarding_steam_cast)
    }
    Text(stringResource(R.string.onboarding_steam_note), style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant)
}

@Composable
private fun FirstLaunchBullet(text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EchoIcon(Icons.Rounded.Check, null, Modifier.padding(top = 2.dp).size(18.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}
