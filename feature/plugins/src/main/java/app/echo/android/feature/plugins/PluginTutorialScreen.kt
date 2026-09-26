package app.echo.android.feature.plugins

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.echo.android.plugin.SamplePlugin

@Composable
internal fun PluginTutorialScreen(
    onBack: () -> Unit,
    onInstallSample: () -> Unit,
) {
    PluginChrome(title = stringResource(R.string.plugins_tutorial_title), onBack = onBack) {
        Text(stringResource(R.string.plugins_tutorial_intro), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.plugins_tutorial_package), style = MaterialTheme.typography.bodyMedium)
        SectionLabel(stringResource(R.string.plugins_tutorial_manifest))
        CodeBlock(SamplePlugin.Manifest.trimIndent())
        Text(stringResource(R.string.plugins_tutorial_hooks), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.plugins_tutorial_api), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.plugins_tutorial_permissions), style = MaterialTheme.typography.bodyMedium)
        SectionLabel(stringResource(R.string.plugins_tutorial_script))
        CodeBlock(SamplePlugin.Script.trimIndent())
        Button(onClick = onInstallSample) { Text(stringResource(R.string.plugins_tutorial_install)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun CodeBlock(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}
