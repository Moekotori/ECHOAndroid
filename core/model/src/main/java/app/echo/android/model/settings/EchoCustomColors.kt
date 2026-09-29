package app.echo.android.model.settings

data class EchoCustomColors(
    val accent: Int,
    val secondary: Int,
    val background: Int,
) {
    fun normalized(): EchoCustomColors = EchoCustomColors(
        accent = accent.toEchoOpaqueColor(),
        secondary = secondary.toEchoOpaqueColor(),
        background = background.toEchoOpaqueColor(),
    )

    companion object {
        val Default = EchoCustomColors(
            accent = 0xFFD3A9B5.toInt(),
            secondary = 0xFFD7675D.toInt(),
            background = 0xFF17171B.toInt(),
        )

        fun fromStored(accent: Int?, secondary: Int?, background: Int?): EchoCustomColors =
            EchoCustomColors(
                accent = accent ?: Default.accent,
                secondary = secondary ?: Default.secondary,
                background = background ?: Default.background,
            ).normalized()
    }
}

fun Int.toEchoOpaqueColor(): Int = (this and 0x00FFFFFF) or 0xFF000000.toInt()

data class EchoSavedColorTheme(
    val id: String,
    val name: String,
    val colors: EchoCustomColors,
)

enum class EchoSavedColorThemeResult {
    Saved,
    Updated,
    Full,
    InvalidName,
}

object EchoSavedColorThemes {
    const val MaxCount = 12
    const val MaxNameLength = 24

    fun normalizeName(name: String): String? =
        name.trim().replace('\n', ' ').replace('\r', ' ').take(MaxNameLength).takeIf { it.isNotEmpty() }

    fun normalize(theme: EchoSavedColorTheme): EchoSavedColorTheme? {
        val name = normalizeName(theme.name) ?: return null
        val id = theme.id.trim().takeIf { it.length in 1..64 && it.none { char -> char.isISOControl() } }
            ?: return null
        return theme.copy(id = id, name = name, colors = theme.colors.normalized())
    }

    fun normalizeAll(themes: List<EchoSavedColorTheme>): List<EchoSavedColorTheme> {
        val seen = HashSet<String>()
        val kept = ArrayList<EchoSavedColorTheme>(MaxCount)
        for (theme in themes) {
            val normalized = normalize(theme) ?: continue
            if (!seen.add(normalized.id)) continue
            kept += normalized
            if (kept.size == MaxCount) break
        }
        return kept
    }

    fun upsert(
        current: List<EchoSavedColorTheme>,
        theme: EchoSavedColorTheme,
    ): List<EchoSavedColorTheme>? {
        val normalized = normalize(theme) ?: return current
        val existing = current.firstOrNull { it.id == normalized.id }
            ?: current.firstOrNull { it.name.equals(normalized.name, ignoreCase = true) }
        return if (existing != null) {
            current.map { saved ->
                if (saved.id == existing.id) normalized.copy(id = existing.id) else saved
            }
        } else if (current.size >= MaxCount) {
            null
        } else {
            current + normalized
        }
    }

    fun remove(current: List<EchoSavedColorTheme>, id: String): List<EchoSavedColorTheme> =
        current.filterNot { it.id == id }
}
