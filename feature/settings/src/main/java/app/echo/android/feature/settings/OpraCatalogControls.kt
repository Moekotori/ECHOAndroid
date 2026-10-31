package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.playback.HeadphoneCorrectionCatalog
import app.echo.android.model.playback.OpraHeadphoneCorrectionState

@Composable
internal fun OpraCatalogControls(
    state: OpraHeadphoneCorrectionState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCatalogSelected: (String?) -> Unit,
    onRefresh: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val autoEq = state.catalog == HeadphoneCorrectionCatalog.AutoEq
    val brandName = state.brands.firstOrNull { it.id == state.selectedBrandId }?.name
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            DspChoices(listOf(stringResource(R.string.autoeq_offline_source), "OPRA"), if (autoEq) 0 else 1, true) { index ->
                if ((index == 0) != autoEq) onCatalogSelected(if (index == 0) "catalog:autoeq" else "catalog:opra")
            }
        }
        if (autoEq) SignalHelpButton(stringResource(R.string.autoeq_offline_source),
            stringResource(R.string.autoeq_catalog_note) +
                (state.status.revision?.let { "\n\n" + stringResource(R.string.autoeq_revision, it.take(12)) } ?: ""))
    }
    TextField(value = state.query, onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(if (brandName == null) stringResource(R.string.opra_search_hint)
            else stringResource(R.string.opra_search_in_brand, brandName), style = MaterialTheme.typography.bodyMedium) },
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(focusedContainerColor = scheme.surfaceContainer,
            unfocusedContainerColor = scheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        leadingIcon = { EchoIcon(Icons.Default.Search, contentDescription = null, tint = scheme.onSurfaceVariant) },
        trailingIcon = if (state.query.isNotEmpty()) ({
            IconButton(onClick = { onQueryChange("") }) {
                EchoIcon(Icons.Default.Close, contentDescription = stringResource(R.string.opra_clear_search), tint = scheme.onSurfaceVariant)
            }
        }) else null,
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (state.status.eqCount > 0) stringResource(R.string.opra_catalog_summary, state.status.productCount, state.status.eqCount)
            else stringResource(R.string.opra_browse_workflow),
            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        IconButton(onClick = {
            if (autoEq) uriHandler.openUri("https://github.com/jaakkopasanen/AutoEq") else onRefresh()
        }, enabled = !state.loading) {
            EchoIcon(if (autoEq) Icons.AutoMirrored.Rounded.OpenInNew else Icons.Default.Refresh,
                contentDescription = stringResource(if (autoEq) R.string.autoeq_check_updates else R.string.diag_refresh_library),
                tint = scheme.onSurfaceVariant)
        }
    }
}
