package app.echo.android.model.playback

data class EchoEqualizerUserPreset(
    val id: String,
    val name: String,
    val updatedAtEpochMs: Long = 0L,
    val parametric: Boolean = false,
    val graphicPresetId: String = EchoEqualizerPreset.Custom,
    val gainsDb: List<Float> = emptyList(),
    val preampDb: Float = 0f,
    val filters: List<OpraEqBand> = emptyList(),
    val sourceLabel: String? = null,
    val opraEqId: String? = null,
) {
    val fromOpra: Boolean
        get() = !opraEqId.isNullOrBlank()
}

object EchoEqualizerUserPresets {
    const val MaxCount = 24
    const val MaxNameLength = 40

    fun normalizeName(name: String): String? =
        name.trim().replace("\n", " ").take(MaxNameLength).takeIf { it.isNotEmpty() }

    fun capture(
        id: String,
        name: String,
        state: EchoEqualizerState,
        updatedAtEpochMs: Long,
        opraEqId: String? = null,
    ): EchoEqualizerUserPreset? {
        val parametric = state.parametric && state.filters.isNotEmpty()
        return EchoEqualizerUserPreset(
            id = id,
            name = name,
            updatedAtEpochMs = updatedAtEpochMs,
            parametric = parametric,
            graphicPresetId = if (parametric) EchoEqualizerPreset.Custom else state.presetId,
            gainsDb = state.gainsDb,
            preampDb = state.preampDb,
            filters = if (parametric) state.filters else emptyList(),
            sourceLabel = state.sourceLabel,
            opraEqId = opraEqId,
        ).let(::normalize)
    }

    fun captureOpra(
        id: String,
        name: String,
        preset: OpraHeadphoneCorrectionPreset,
        updatedAtEpochMs: Long,
    ): EchoEqualizerUserPreset? {
        if (preset.bands.isEmpty()) return null
        return normalize(
            EchoEqualizerUserPreset(
                id = id,
                name = name.ifBlank { preset.displayName },
                updatedAtEpochMs = updatedAtEpochMs,
                parametric = true,
                graphicPresetId = EchoEqualizerPreset.Custom,
                gainsDb = emptyList(),
                preampDb = preset.preampDb,
                filters = preset.bands,
                sourceLabel = preset.displayName,
                opraEqId = preset.eqId,
            ),
        )
    }

    fun upsert(
        current: List<EchoEqualizerUserPreset>,
        preset: EchoEqualizerUserPreset,
    ): List<EchoEqualizerUserPreset>? {
        val normalized = normalize(preset) ?: return current
        val replaceId = current.firstOrNull { it.id == normalized.id }?.id
            ?: normalized.opraEqId?.let { eqId -> current.firstOrNull { it.opraEqId == eqId }?.id }
        return if (replaceId != null) {
            current.map { existing ->
                if (existing.id == replaceId) normalized.copy(id = replaceId) else existing
            }.sortedByDescending { it.updatedAtEpochMs }
        } else if (current.size >= MaxCount) {
            null
        } else {
            (listOf(normalized) + current).take(MaxCount)
        }
    }

    fun rename(
        current: List<EchoEqualizerUserPreset>,
        id: String,
        name: String,
        updatedAtEpochMs: Long,
    ): List<EchoEqualizerUserPreset> {
        val normalizedName = normalizeName(name) ?: return current
        return current.map { preset ->
            if (preset.id == id) {
                preset.copy(name = normalizedName, updatedAtEpochMs = updatedAtEpochMs)
            } else {
                preset
            }
        }
    }

    fun remove(current: List<EchoEqualizerUserPreset>, id: String): List<EchoEqualizerUserPreset> =
        current.filterNot { it.id == id }

    fun opraFavorites(current: List<EchoEqualizerUserPreset>): List<EchoEqualizerUserPreset> =
        current.filter { it.fromOpra }

    fun normalize(preset: EchoEqualizerUserPreset): EchoEqualizerUserPreset? {
        val safeName = normalizeName(preset.name) ?: return null
        if (preset.id.isBlank()) return null
        val keepParametric = preset.parametric && preset.filters.isNotEmpty()
        val graphicId = if (keepParametric) {
            EchoEqualizerPreset.Custom
        } else {
            EchoEqualizerPresets.normalizePresetId(preset.graphicPresetId)
        }
        return preset.copy(
            name = safeName,
            parametric = keepParametric,
            graphicPresetId = graphicId,
            gainsDb = if (keepParametric) {
                preset.gainsDb.filter { it.isFinite() }
            } else {
                EchoEqualizerPresets.defaultBands(preset.gainsDb).map { it.gainDb }
            },
            preampDb = preset.preampDb.takeIf { it.isFinite() }?.coerceIn(-24f, 12f) ?: 0f,
            filters = if (keepParametric) preset.filters else emptyList(),
            sourceLabel = preset.sourceLabel?.trim()?.takeIf { it.isNotEmpty() },
            opraEqId = preset.opraEqId?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}
