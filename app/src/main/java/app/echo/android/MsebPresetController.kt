package app.echo.android

import app.echo.android.data.EchoSettingsStore
import app.echo.android.feature.settings.MsebPresetActions
import app.echo.android.model.playback.*
import java.util.UUID

internal fun msebPresetActions(store: EchoSettingsStore, presets: List<EchoMsebUserPreset>) = MsebPresetActions(
    presets = presets,
    save = { name, values, id -> store.editMsebPresets { current ->
        val preset = requireNotNull(EchoMsebUserPresets.normalize(EchoMsebUserPreset(id ?: UUID.randomUUID().toString(), name, values)))
        if (id == null) { require(current.size < EchoMsebUserPresets.MaxCount); current + preset }
        else { require(current.any { it.id == id }); current.map { if (it.id == id) preset else it } }
    } },
    rename = { id, name -> store.editMsebPresets { current -> current.map {
        if (it.id == id) requireNotNull(EchoMsebUserPresets.normalize(it.copy(name = name))) else it
    } } },
    delete = { id -> store.editMsebPresets { current -> current.filterNot { it.id == id } } },
    importPresets = { incoming -> store.editMsebPresets { current -> EchoMsebUserPresets.merge(current, incoming) { UUID.randomUUID().toString() } } },
)
