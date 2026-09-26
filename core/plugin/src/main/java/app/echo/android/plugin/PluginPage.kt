package app.echo.android.plugin

data class PluginPageDocument(
    val title: String,
    val items: List<PluginPageItem>,
)

sealed class PluginPageItem {
    data class Text(val text: String) : PluginPageItem()
    data class Button(val id: String, val label: String) : PluginPageItem()
    data class Rows(val rows: List<Row>) : PluginPageItem()
    data class Row(val title: String, val subtitle: String)
}

internal data class RawPluginPage(
    val title: String?,
    val items: List<RawPluginItem>,
)

internal data class RawPluginItem(
    val type: String?,
    val text: String?,
    val id: String?,
    val label: String?,
    val rows: List<Pair<String?, String?>>,
)

internal fun parsePluginPage(raw: RawPluginPage): PluginPageDocument? {
    val title = raw.title?.trim()?.take(80)?.takeIf { it.isNotEmpty() } ?: return null
    if (raw.items.size > 40) return null
    val items = raw.items.mapNotNull { item ->
        when (item.type) {
            "text" -> item.text?.trim()?.take(500)?.takeIf { it.isNotEmpty() }?.let(PluginPageItem::Text)
            "button" -> {
                val id = item.id?.trim().orEmpty()
                val label = item.label?.trim()?.take(80).orEmpty()
                if (!PluginPaths.isActionId(id) || label.isEmpty()) null
                else PluginPageItem.Button(id, label)
            }
            "list" -> {
                val rows = item.rows.take(30).mapNotNull { (rowTitle, subtitle) ->
                    val safeTitle = rowTitle?.trim()?.take(120).orEmpty()
                    if (safeTitle.isEmpty()) null
                    else PluginPageItem.Row(safeTitle, subtitle?.trim()?.take(180).orEmpty())
                }
                if (rows.isEmpty()) null else PluginPageItem.Rows(rows)
            }
            else -> null
        }
    }
    return PluginPageDocument(title, items)
}
