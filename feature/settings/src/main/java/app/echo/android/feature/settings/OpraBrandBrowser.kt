package app.echo.android.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoClickable
import app.echo.android.model.playback.OpraHeadphoneBrand
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import app.echo.android.design.EchoIcon

internal fun androidx.compose.foundation.lazy.LazyListScope.opraBrandItems(
    brands: List<OpraHeadphoneBrand>,
    enabled: Boolean,
    onBrandSelected: (String) -> Unit,
) {
    items(brands, key = { "brand:${it.id}" }, contentType = { "brand" }) { brand ->
        OpraBrandRow(brand = brand, enabled = enabled, onClick = { onBrandSelected(brand.id) })
    }
}

@Composable
internal fun OpraSelectedBrand(brand: OpraHeadphoneBrand, enabled: Boolean, backLabel: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = enabled, contentPadding = PaddingValues(horizontal = 0.dp)) {
            EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(backLabel)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(brand.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            SignalNote(pluralStringResource(R.plurals.opra_brand_models, brand.productCount, brand.productCount))
        }
    }
}

@Composable
private fun OpraBrandRow(
    brand: OpraHeadphoneBrand,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .echoClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OpraBrandLogo(name = brand.name, logoUrl = brand.logoUrl, size = 36.dp)
        Text(
            brand.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            pluralStringResource(R.plurals.opra_brand_models, brand.productCount, brand.productCount),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
            modifier = Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
    }
    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable
internal fun OpraBrandLogo(name: String, logoUrl: String?, size: Dp) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val initial = remember(name) { name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?" }
    var failed by remember(logoUrl) { mutableStateOf(false) }
    val showImage = !logoUrl.isNullOrBlank() && !failed
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(if (showImage) Color.White else scheme.surfaceContainerHigh)
            .then(if (showImage) Modifier.border(1.dp, scheme.outlineVariant.copy(alpha = 0.45f), shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (showImage) {
            val context = LocalContext.current
            val density = androidx.compose.ui.platform.LocalDensity.current
            val request = remember(context, logoUrl, size, density) {
                ImageRequest.Builder(context)
                    .data(logoUrl)
                    .size(with(density) { size.toPx() }.toInt().coerceIn(32, 192))
                    .crossfade(false)
                    .setHeader("User-Agent", "ECHOAndroid/1.0")
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(if (size < 48.dp) 4.dp else 6.dp),
                contentScale = ContentScale.Fit,
                onError = { failed = true },
            )
        } else {
            Text(
                initial,
                style = if (size < 40.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
