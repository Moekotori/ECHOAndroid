package app.echo.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

private const val ISSUES_URL = "https://github.com/Moekotori/echoandroid/issues"

/** Posting etiquette: short, plain, and shown once to everyone. */
@Composable
internal fun FirstLaunchMannersContent(onOpenLink: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(colors.tertiaryContainer, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.onboarding_manners_quote), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = colors.onTertiaryContainer)
        Text(stringResource(R.string.onboarding_manners_quote_body), style = MaterialTheme.typography.bodyMedium,
            color = colors.onTertiaryContainer.copy(alpha = 0.85f))
    }
    FirstLaunchRuleCard(
        title = R.string.onboarding_do_title,
        titleColor = colors.primary,
        mark = "✓",
        items = listOf(R.string.onboarding_do_1, R.string.onboarding_do_2, R.string.onboarding_do_3),
    )
    FirstLaunchRuleCard(
        title = R.string.onboarding_dont_title,
        titleColor = colors.error,
        mark = "✕",
        items = listOf(R.string.onboarding_dont_1, R.string.onboarding_dont_2, R.string.onboarding_dont_3),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.onboarding_sample_label), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.onboarding_sample_text), style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        EchoIcon(Icons.Rounded.Forum, null, Modifier.padding(top = 2.dp))
        Column {
            Text(stringResource(R.string.onboarding_feedback_title), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { onOpenLink(ISSUES_URL) }) {
                Text(stringResource(R.string.onboarding_feedback_link))
            }
        }
    }
}

@Composable
private fun FirstLaunchRuleCard(title: Int, titleColor: Color, mark: String, items: List<Int>) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(colors.surfaceContainer, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.labelMedium, color = titleColor)
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(mark, style = MaterialTheme.typography.bodyMedium, color = titleColor)
                Text(stringResource(item), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
