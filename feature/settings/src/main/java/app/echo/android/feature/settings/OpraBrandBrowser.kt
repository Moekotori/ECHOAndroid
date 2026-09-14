package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.OpraHeadphoneCorrectionState

@Composable
internal fun OpraBrandBrowser(state: OpraHeadphoneCorrectionState, onBrandSelected: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.selectedBrandId != null || state.query.isNotBlank()) {
            TextButton(onClick = { onBrandSelected(null) }, enabled = !state.loading) {
                Text(stringResource(R.string.opra_all_brands))
            }
            state.brands.firstOrNull { it.id == state.selectedBrandId }?.let {
                Text(it.name, style = MaterialTheme.typography.titleLarge)
            }
        } else if (state.brands.isNotEmpty()) {
            Text(stringResource(R.string.opra_choose_brand), style = MaterialTheme.typography.titleMedium)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.brands, key = { it.id }) { brand ->
                    Surface(onClick = { onBrandSelected(brand.id) }, enabled = !state.loading,
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(brand.name, modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                            style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}
