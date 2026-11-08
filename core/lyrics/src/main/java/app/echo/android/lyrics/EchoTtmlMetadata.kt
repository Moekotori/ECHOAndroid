package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricAlternateText
import app.echo.android.model.lyrics.EchoLyricLine
import org.w3c.dom.Element

/** Head annotations refer to body lines by xml:id / for, rather than by timestamp. */
internal class EchoTtmlMetadata(root: Element) {
    private data class Agent(val name: String?, val type: String?)
    private val agents = LinkedHashMap<String, Agent>()
    private val alternates = HashMap<String, MutableList<EchoLyricAlternateText>>()

    init {
        for (element in root.ttmlDescendants()) {
            if (element.ttmlTag() == "agent" && agents.size < 64) {
                val id = element.ttmlAttr("id").takeIf { it.length in 1..128 } ?: continue
                val name = element.ttmlDescendants().firstOrNull { it.ttmlTag() == "name" }
                    ?.textContent?.trim()?.takeIf { it.length in 1..2000 }
                agents.putIfAbsent(id, Agent(name, element.ttmlAttr("type").takeIf { it.length in 1..128 }))
            }
            if (element.ttmlTag() != "text") continue
            var parent: Element? = element
            var target: String? = null
            var role: String? = null
            while (parent != null) {
                val current = parent
                if (target == null) target = listOf("for", "target", "key").firstNotNullOfOrNull {
                    current.ttmlAttr(it).takeIf(String::isNotBlank)
                }
                if (role == null) {
                    val kind = (current.ttmlAttr("role") + " " + current.ttmlAttr("type") + " " + current.ttmlTag()).lowercase()
                    role = when {
                        "roman" in kind || "transliteration" in kind -> "romanization"
                        "translation" in kind || "subtitle" in kind -> "translation"
                        else -> null
                    }
                }
                parent = current.parentNode as? Element
            }
            val id = target?.removePrefix("#")?.takeIf { it.length in 1..128 } ?: continue
            val value = element.textContent.replace(Regex("\\s+"), " ").trim().takeIf { it.length in 1..2000 } ?: continue
            if (id !in alternates && alternates.size >= 2000) continue
            val rows = alternates.getOrPut(id) { ArrayList() }
            val alternate = EchoLyricAlternateText(role ?: "translation", value, element.ttmlAttr("lang").takeIf { it.length in 1..64 })
            if (rows.size < 8 && alternate !in rows) rows += alternate
        }
    }

    fun enrich(element: Element, line: EchoLyricLine): EchoLyricLine {
        val texts = (alternates[element.ttmlAttr("id")].orEmpty() + line.alternateTexts).distinct().take(8)
        val agentId = line.speaker
        return line.copy(
            translation = texts.firstOrNull { it.role == "translation" }?.text ?: line.translation,
            romanization = texts.firstOrNull { it.role == "romanization" }?.text ?: line.romanization,
            alternateTexts = texts,
            speakerId = agentId,
            speaker = agents[agentId]?.name ?: agentId,
        )
    }

    fun entries(): Map<String, String> = buildMap {
        agents.forEach { (id, agent) ->
            agent.name?.let { put("ttml_agent.$id.name", it) }
            agent.type?.let { put("ttml_agent.$id.type", it) }
        }
    }
}

internal fun Element.ttmlTag(): String = localName ?: tagName.substringAfter(':')
internal fun Element.ttmlAttr(name: String): String {
    for (i in 0 until attributes.length) {
        val attribute = attributes.item(i)
        if ((attribute.localName ?: attribute.nodeName.substringAfter(':')) == name) return attribute.nodeValue
    }
    return ""
}
internal fun Element.ttmlDescendants(): Sequence<Element> = sequence {
    for (i in 0 until childNodes.length) {
        val child = childNodes.item(i) as? Element ?: continue
        yield(child)
        yieldAll(child.ttmlDescendants())
    }
}
