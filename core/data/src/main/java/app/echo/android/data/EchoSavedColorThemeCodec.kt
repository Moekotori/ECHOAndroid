package app.echo.android.data

import app.echo.android.model.settings.EchoCustomColors
import app.echo.android.model.settings.EchoSavedColorTheme
import app.echo.android.model.settings.EchoSavedColorThemes
import org.json.JSONArray
import org.json.JSONObject

internal object EchoSavedColorThemeCodec {
    fun encode(themes: List<EchoSavedColorTheme>): String {
        val array = JSONArray()
        EchoSavedColorThemes.normalizeAll(themes).forEach { theme ->
            array.put(
                JSONObject()
                    .put("id", theme.id)
                    .put("name", theme.name)
                    .put("accent", theme.colors.accent)
                    .put("secondary", theme.colors.secondary)
                    .put("background", theme.colors.background),
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<EchoSavedColorTheme> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            EchoSavedColorThemes.normalizeAll(
                List(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@List null
                    EchoSavedColorTheme(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        colors = EchoCustomColors(
                            accent = item.optInt("accent"),
                            secondary = item.optInt("secondary"),
                            background = item.optInt("background"),
                        ),
                    )
                }.filterNotNull(),
            )
        }.getOrDefault(emptyList())
    }
}
