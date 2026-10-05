package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.playback.EchoShuffleMode
import app.echo.android.model.playback.EchoShuffleOptions

@Composable
internal fun SettingsShuffleModeRow(options: EchoShuffleOptions, onChange: (EchoShuffleOptions) -> Unit) {
    val value = options.normalized
    val scheme = MaterialTheme.colorScheme
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    SettingsSectionCard(title = stringResource(R.string.settings_shuffle_mode)) {
        Text(
            stringResource(R.string.settings_shuffle_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ShuffleModeChoice(
                title = stringResource(R.string.settings_shuffle_pseudo),
                summary = stringResource(R.string.settings_shuffle_pseudo_summary),
                badge = stringResource(R.string.settings_shuffle_recommended),
                selected = value.mode == EchoShuffleMode.PseudoRandom,
                onClick = { onChange(value.copy(mode = EchoShuffleMode.PseudoRandom)) },
            )
            ShuffleModeChoice(
                title = stringResource(R.string.settings_shuffle_pure),
                summary = stringResource(R.string.settings_shuffle_pure_summary),
                badge = stringResource(R.string.settings_shuffle_surprise),
                selected = value.mode == EchoShuffleMode.PureRandom,
                onClick = { onChange(value.copy(mode = EchoShuffleMode.PureRandom)) },
            )
        }
        ShuffleSequencePreview(value)
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
        when (value.mode) {
            EchoShuffleMode.PseudoRandom -> SettingsSwitchRow(
                title = stringResource(R.string.settings_shuffle_spread_artists),
                detail = stringResource(R.string.settings_shuffle_spread_artists_detail),
                checked = value.spreadArtists,
                onCheckedChange = { onChange(value.copy(spreadArtists = it)) },
            )
            EchoShuffleMode.PureRandom -> SettingsChoiceGroupRow(
                title = stringResource(R.string.settings_shuffle_recent),
                detail = stringResource(R.string.settings_shuffle_recent_detail),
                options = EchoShuffleOptions.RecentTrackCounts.map { count ->
                    SettingsChoiceOption(count.toString(),
                        if (count == 0) stringResource(R.string.settings_shuffle_recent_off)
                        else stringResource(R.string.settings_shuffle_recent_count, count))
                },
                selectedValue = value.recentTrackCount.toString(),
                onOptionSelected = { onChange(value.copy(recentTrackCount = it.toInt())) },
            )
        }
        SettingsDisclosureRow(
            title = stringResource(R.string.settings_shuffle_rules),
            detail = stringResource(R.string.settings_shuffle_rules_summary),
            expanded = detailsExpanded,
            onExpandedChange = { detailsExpanded = it },
        )
        if (detailsExpanded) {
            SettingsInfoRow(
                stringResource(R.string.settings_shuffle_rule_round),
                stringResource(if (value.mode == EchoShuffleMode.PseudoRandom)
                    R.string.settings_shuffle_pseudo_detail else R.string.settings_shuffle_pure_detail),
            )
            if (value.mode == EchoShuffleMode.PureRandom) SettingsInfoRow(
                stringResource(R.string.settings_shuffle_rule_fair),
                stringResource(R.string.settings_shuffle_rule_fair_detail),
            )
            SettingsInfoRow(
                stringResource(R.string.settings_shuffle_rule_requests),
                stringResource(R.string.settings_shuffle_rule_requests_detail),
            )
            SettingsInfoRow(
                stringResource(R.string.settings_shuffle_rule_apply),
                stringResource(R.string.settings_shuffle_scope),
            )
        }
        SettingsActionRow(
            title = stringResource(R.string.settings_shuffle_reset),
            detail = stringResource(R.string.settings_shuffle_reset_detail),
            enabled = value != EchoShuffleOptions(),
            onClick = { onChange(EchoShuffleOptions()) },
        )
    }
}

@Composable
private fun ShuffleModeChoice(
    title: String,
    summary: String,
    badge: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).clip(SettingsShape)
            .background(scheme.onSurface.copy(alpha = if (selected) 0.06f else 0f))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EchoIcon(
            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            null, Modifier.size(22.dp), tint = scheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(badge, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}
