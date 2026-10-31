package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackAudioQuality
import java.util.Locale

@Composable
internal fun TrackVersionAutomaticRow(selected: Boolean, quality: Boolean, enabled: Boolean, onClick: () -> Unit) {
    VersionSelectionRow(selected, enabled, onClick) {
        Text(stringResource(if (quality) R.string.duplicates_auto else R.string.track_versions_auto),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Text(stringResource(R.string.duplicates_auto_support), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun TrackVersionChoiceRow(track: EchoTrack, quality: EchoTrackAudioQuality?, location: String?, selected: Boolean,
    recommended: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val locale = LocalResources.current.configuration.locales[0]
    val mime = (quality?.codecMime ?: track.mimeType).orEmpty().substringBefore(';').lowercase(Locale.ROOT)
    val format = when (mime) {
        "audio/raw" -> "PCM"
        "audio/wav", "audio/x-wav" -> "WAV"
        "audio/flac", "audio/x-flac", "application/flac", "application/x-flac" -> "FLAC"
        "audio/alac" -> "ALAC"
        "audio/mpeg" -> "MP3"
        "audio/mp4a-latm", "audio/aac" -> "AAC"
        "audio/opus" -> "Opus"
        else -> track.fileName?.substringAfterLast('.', "")?.uppercase(locale)?.takeIf(String::isNotEmpty)
            ?: stringResource(R.string.duplicates_audio_file)
    }
    val rate = (quality?.sampleRateHz ?: track.sampleRateHz)?.takeIf { it > 0 }?.let {
        if (it % 1000 == 0) "${it / 1000} kHz" else "${String.format(locale, "%.1f", it / 1000.0)} kHz"
    }
    val technical = listOfNotNull(quality?.bitDepth?.takeIf { it > 0 }?.let { "$it bit" }, rate,
        quality?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" }).joinToString(" · ")
    VersionSelectionRow(selected, enabled, onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(format, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (recommended) VersionBadge(stringResource(R.string.duplicates_recommended_short))
            if (selected) VersionBadge(stringResource(R.string.duplicates_preferred_short))
        }
        Text(technical.ifBlank { stringResource(R.string.duplicates_unknown_quality) }, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val source = when (track.source.id.substringBefore(':')) {
            "mediastore", "saf" -> stringResource(R.string.feature_library_local_library_ddf4b1)
            "subsonic" -> "Subsonic / Navidrome"
            "jellyfin" -> "Jellyfin / Emby"
            "webdav" -> "WebDAV"
            "smb" -> "SMB"
            "echo-link" -> "Echo Link"
            else -> stringResource(R.string.feature_library_unknown_source_d8c291)
        }
        Text(listOfNotNull(source, location?.takeIf(String::isNotBlank) ?: track.fileName).joinToString(" · "), maxLines = 2,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VersionSelectionRow(selected: Boolean, enabled: Boolean, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(if (selected) scheme.surfaceContainerHigh else scheme.surface.copy(alpha = 0f))
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).heightIn(min = 72.dp)
        .padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected, null, enabled = enabled, modifier = Modifier.size(28.dp),
            colors = RadioButtonDefaults.colors(selectedColor = scheme.onSurface))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun VersionBadge(label: String) {
    Text(label, Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}
