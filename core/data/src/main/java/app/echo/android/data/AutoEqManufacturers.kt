package app.echo.android.data

import java.io.InputStream
import java.util.Locale

/** Canonical brands and aliases from the same pinned AutoEQ revision as the profiles. */
internal class AutoEqManufacturers(input: InputStream) {
    private val names = buildMap {
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter(String::isNotBlank).forEach { line ->
                val aliases = line.split('\t')
                aliases.forEach { put(it.lowercase(Locale.ROOT), aliases.first()) }
            }
        }
    }

    fun brandFor(model: String): String {
        // Longest whole-word prefix keeps multiword brands and their aliases together.
        var candidate = model.lowercase(Locale.ROOT)
        while (candidate.isNotEmpty()) {
            names[candidate]?.let { return it }
            candidate = candidate.substringBeforeLast(' ', "")
        }
        error("autoeq_unknown_manufacturer: $model")
    }
}
