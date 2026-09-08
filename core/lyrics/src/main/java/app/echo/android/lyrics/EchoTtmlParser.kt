package app.echo.android.lyrics

import app.echo.android.model.lyrics.*
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/** Apple/AMLL lyric profile: media-absolute timestamps, including nested backing vocals. */
internal object EchoTtmlParser {
    fun parse(text: String, sourceLabel: String?): EchoLyrics {
        require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true)) {
            "External XML declarations are not supported in lyrics"
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
        val document = builder.parse(InputSource(StringReader(text)))
        val metadata = linkedMapOf<String, String>()
        document.documentElement.descendants().filter { it.tag() == "meta" }.forEach {
            if (it.attr("key").isNotBlank()) metadata[it.attr("key")] = it.attr("value")
        }
        val lines = mutableListOf<EchoLyricLine>()
        document.documentElement.descendants().filter { it.tag() == "p" }.forEach { p ->
            parseLine(p, inheritedStart = null, inheritedEnd = null, background = false)?.let(lines::add)
            p.descendants().filter { it.attr("role") == "x-bg" }.forEach { bg ->
                parseLine(bg, clock(p.attr("begin")), end(p, clock(p.attr("begin")) ?: 0L), true,
                    p.attr("agent").ifBlank { null })?.let(lines::add)
            }
        }
        val sorted = lines.sortedBy { it.startMs }
        return EchoLyrics(
            lines = sorted.mapIndexed { index, line ->
                val lineEnd = line.endMs ?: sorted.asSequence().drop(index + 1)
                    .firstOrNull { it.startMs > line.startMs }?.startMs
                line.copy(endMs = lineEnd, words = line.words.mapIndexed { i, word ->
                    word.copy(endMs = word.endMs ?: line.words.getOrNull(i + 1)?.startMs ?: lineEnd)
                })
            },
            metadata = metadata,
            sourceLabel = sourceLabel,
            format = EchoLyricsFormat.Ttml,
        )
    }

    private fun parseLine(
        element: Element, inheritedStart: Long?, inheritedEnd: Long?, background: Boolean,
        inheritedSpeaker: String? = null,
    ): EchoLyricLine? {
        val start = clock(element.attr("begin")) ?: inheritedStart
            ?: element.descendants().mapNotNull { clock(it.attr("begin")) }.minOrNull() ?: return null
        val finish = end(element, start) ?: inheritedEnd
        val words = mutableListOf<EchoLyricWord>()
        val fullText = StringBuilder()
        var translation: String? = null
        var romanization: String? = null
        fun visit(node: Node, wordStart: Long?, wordEnd: Long?) {
            if (node is Element) {
                when (node.attr("role")) {
                    "x-bg" -> if (node !== element) return
                    "x-translation" -> { translation = node.textContent.trim(); return }
                    "x-roman" -> { romanization = node.textContent.trim(); return }
                }
                if (node.tag() == "br") { fullText.append('\n'); if (words.isNotEmpty()) {
                    val last = words.last(); words[words.lastIndex] = last.copy(text = last.text + "\n")
                }; return }
                val begin = clock(node.attr("begin")) ?: wordStart
                val stop = end(node, begin ?: start) ?: wordEnd
                for (i in 0 until node.childNodes.length) visit(node.childNodes.item(i), begin, stop)
            } else if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
                val raw = node.nodeValue.orEmpty()
                // Formatting indentation is not a sung space. Explicit inline spaces are preserved.
                if (raw.isBlank() && (raw.contains('\n') || raw.contains('\r'))) return
                val value = raw.replace(Regex("[\\t\\r\\n ]+"), " ")
                if (value.isEmpty()) return
                fullText.append(value)
                if (wordStart != null && value.isNotBlank()) {
                    words += EchoLyricWord(wordStart, wordEnd, value)
                } else if (words.isNotEmpty()) {
                    val last = words.last()
                    words[words.lastIndex] = last.copy(text = last.text + value)
                } else {
                    // Keep leading un-timed text rather than dropping it when timed spans follow.
                    words += EchoLyricWord(start, null, value)
                }
            }
        }
        // p timing belongs to the line, not to otherwise untimed child text.
        for (i in 0 until element.childNodes.length) visit(element.childNodes.item(i), null, null)
        if (fullText.isBlank()) return null
        val hasTimedSpans = element.descendants().any { it.attr("begin").isNotBlank() && it.attr("role").isBlank() }
        return EchoLyricLine(start, finish, fullText.toString(), translation, romanization,
            if (hasTimedSpans) words else emptyList(),
            speaker = element.attr("agent").ifBlank { inheritedSpeaker }, isBackground = background)
    }

    private fun end(element: Element, start: Long): Long? =
        clock(element.attr("end")) ?: clock(element.attr("dur"))?.let { start + it }

    private fun clock(raw: String): Long? {
        if (raw.isBlank()) return null
        val value = raw.trim()
        if (value.endsWith("ms")) return value.dropLast(2).toDoubleOrNull()?.toLong()
        if (value.endsWith("s")) return value.dropLast(1).toDoubleOrNull()?.times(1000)?.toLong()
        val parts = value.split(':').map { it.toDoubleOrNull() ?: return null }
        if (parts.size !in 1..3) return null
        return (parts.fold(0.0) { sum, n -> sum * 60 + n } * 1000).toLong()
    }

    private fun Element.tag(): String = localName ?: tagName.substringAfter(':')
    private fun Element.attr(name: String): String {
        for (i in 0 until attributes.length) {
            val a = attributes.item(i)
            if ((a.localName ?: a.nodeName.substringAfter(':')) == name) return a.nodeValue
        }
        return ""
    }
    private fun Element.descendants(): Sequence<Element> = sequence {
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i) as? Element ?: continue
            yield(child)
            yieldAll(child.descendants())
        }
    }
}
