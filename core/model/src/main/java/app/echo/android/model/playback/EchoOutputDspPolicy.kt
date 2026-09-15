package app.echo.android.model.playback

object EchoOutputDspPolicy {
    const val MaxBindings = 16

    fun deviceKey(kind: EchoOutputDeviceKind, name: String?): String {
        val trimmed = name?.trim()?.take(80).orEmpty()
        return if (trimmed.isEmpty()) kind.id else "${kind.id}:$trimmed"
    }

    fun kindKey(kind: EchoOutputDeviceKind): String = kind.id

    fun resolvePresetId(bindings: Map<String, String>, key: String): String? {
        bindings[key]?.takeIf { it.isNotBlank() }?.let { return it }
        val kind = key.substringBefore(':', missingDelimiterValue = key)
        return bindings[kind]?.takeIf { it.isNotBlank() }
    }

    fun bind(
        current: Map<String, String>,
        key: String,
        presetId: String,
    ): Map<String, String> {
        val safeKey = key.trim().take(120)
        val safeId = presetId.trim()
        if (safeKey.isEmpty() || safeId.isEmpty()) return current
        val without = current.filterKeys { it != safeKey }
        val next = without + (safeKey to safeId)
        return if (next.size <= MaxBindings) {
            next
        } else {
            next.entries.toList().takeLast(MaxBindings).associate { it.key to it.value }
        }
    }

    fun unbind(current: Map<String, String>, key: String): Map<String, String> =
        current.filterKeys { it != key }
}
