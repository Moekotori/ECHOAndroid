package app.echo.android.plugin

import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.WrapFactory
import org.mozilla.javascript.Wrapper

internal class ScriptFault(val code: String, val detail: String?)

internal class PluginScriptCallbacks(
    val onPage: (PluginPageDocument) -> Unit,
    val onLog: (String) -> Unit,
    val schedule: (Long, Function) -> Map<String, Any?>,
    val cancel: (Int) -> Map<String, Any?>,
)

/**
 * One plugin's Rhino scope. Callers stay on the plugin thread.
 * Optimization stays at -1 because Android rejects the bytecode Rhino would otherwise generate.
 */
internal class PluginScriptSession(
    private val factory: PluginContextFactory,
    script: String,
    private val grants: () -> Set<PluginCapability>,
    private val services: PluginServices,
    private val storage: PluginStorage,
    private val callbacks: PluginScriptCallbacks,
) {
    private val scope: Scriptable = factory.call { context ->
        arm(context)
        try {
            val created = context.initSafeStandardObjects()
            installBridge(context, created)
            context.evaluateString(created, script, "main.js", 1, null)
            if (context.getThreadLocal(TimedOut) == true) throw PluginTimeoutError()
            created
        } finally {
            disarm(context)
        }
    } as Scriptable

    fun callHook(name: String, argument: Any? = null): ScriptFault? = invoke {
        val function = ScriptableObject.getProperty(scope, name)
        if (function is Function) {
            val args = if (argument == null) emptyArray() else arrayOf(toJs(argument))
            function.call(Context.getCurrentContext(), scope, scope, args)
        }
    }

    fun invoke(function: Function): ScriptFault? = invoke {
        function.call(Context.getCurrentContext(), scope, scope, emptyArray())
    }

    internal fun debugEval(source: String): String = factory.call { context ->
        arm(context)
        try {
            val value = context.evaluateString(scope, source, "test.js", 1, null)
            if (context.getThreadLocal(TimedOut) == true) throw PluginTimeoutError()
            Context.toString(value)
        } finally {
            disarm(context)
        }
    } as String

    private fun invoke(block: () -> Unit): ScriptFault? = try {
        factory.call { context ->
            arm(context)
            try {
                block()
                if (context.getThreadLocal(TimedOut) == true) throw PluginTimeoutError()
            } finally {
                disarm(context)
            }
            null
        }
        null
    } catch (_: PluginTimeoutError) {
        ScriptFault("timeout", null)
    } catch (error: StackOverflowError) {
        ScriptFault("script", "stack")
    } catch (error: Exception) {
        ScriptFault("script", clean(error))
    }

    private fun installBridge(context: Context, scope: Scriptable) {
        val echo = context.newObject(scope)
        ScriptableObject.putProperty(scope, "echo", echo)
        val playback = context.newObject(scope)
        val library = context.newObject(scope)
        val stored = context.newObject(scope)
        val net = context.newObject(scope)
        val ui = context.newObject(scope)
        put(echo, "playback", playback)
        put(echo, "library", library)
        put(echo, "storage", stored)
        put(echo, "net", net)
        put(echo, "ui", ui)
        putFn(scope, echo, "log") { args ->
            callbacks.onLog(jsString(arg(args, 0))?.toLogLine() ?: "log")
            ok(scope)
        }
        putFn(scope, echo, "after") { args ->
            val delay = jsLong(arg(args, 0))
            val function = arg(args, 1) as? Function
            if (delay == null || function == null) fail(scope, "invalid")
            else context.result(scope, callbacks.schedule(delay, function))
        }
        putFn(scope, echo, "cancel") { args ->
            val id = jsLong(arg(args, 0))?.toInt()
            if (id == null) fail(scope, "invalid") else context.result(scope, callbacks.cancel(id))
        }
        putFn(scope, playback, "now") { now(scope) }
        putFn(scope, playback, "play") { transport(scope, TransportCommand.Play) }
        putFn(scope, playback, "pause") { transport(scope, TransportCommand.Pause) }
        putFn(scope, playback, "next") { transport(scope, TransportCommand.Next) }
        putFn(scope, playback, "previous") { transport(scope, TransportCommand.Previous) }
        putFn(scope, playback, "seek") { args ->
            if (!granted(PluginCapability.PlaybackControl)) return@putFn fail(scope, "not_granted")
            val position = jsLong(arg(args, 0))
            if (position == null || position !in 0..MaxPositionMs) fail(scope, "invalid")
            else transport(scope, TransportCommand.Seek(position))
        }
        putFn(scope, library, "search") { args -> search(scope, args) }
        putFn(scope, stored, "get") { args -> storageGet(scope, args) }
        putFn(scope, stored, "set") { args -> storageSet(scope, args) }
        putFn(scope, net, "fetch") { args -> fetch(scope, args) }
        putFn(scope, ui, "setPage") { args -> setPage(scope, args) }
    }

    private fun now(scope: Scriptable): Scriptable {
        if (!granted(PluginCapability.PlaybackRead)) return fail(scope, "not_granted")
        val snapshot = runCatching { services.playbackSnapshot() }.getOrNull()
            ?: return fail(scope, "failed")
        return ok(
            scope,
            mapOf(
                "title" to snapshot.title.take(200),
                "artist" to snapshot.artist.take(200),
                "album" to snapshot.album.take(200),
                "playing" to snapshot.playing,
                "positionMs" to snapshot.positionMs.coerceIn(0, MaxPositionMs),
                "durationMs" to snapshot.durationMs.coerceIn(0, MaxPositionMs),
            ),
        )
    }

    private fun transport(scope: Scriptable, command: TransportCommand): Scriptable {
        if (!granted(PluginCapability.PlaybackControl)) return fail(scope, "not_granted")
        if (runCatching { services.transport(command) }.isFailure) return fail(scope, "failed")
        return ok(scope)
    }

    private fun search(scope: Scriptable, args: Array<out Any?>): Scriptable {
        if (!granted(PluginCapability.LibrarySearch)) return fail(scope, "not_granted")
        val query = jsString(arg(args, 0))?.trim()?.take(80) ?: return fail(scope, "invalid")
        if (query.isEmpty()) return ok(scope, mapOf("tracks" to emptyList<Map<String, String>>()))
        val tracks = runCatching { services.searchLibrary(query) }.getOrNull() ?: return fail(scope, "failed")
        return ok(
            scope,
            mapOf(
                "tracks" to tracks.take(20).map { hit ->
                    val safe = hit.forScript()
                    mapOf("id" to safe.id, "title" to safe.title, "artist" to safe.artist, "album" to safe.album)
                },
            ),
        )
    }

    private fun storageGet(scope: Scriptable, args: Array<out Any?>): Scriptable {
        if (!granted(PluginCapability.Storage)) return fail(scope, "not_granted")
        val key = jsString(arg(args, 0)) ?: return fail(scope, "invalid")
        return ok(scope, mapOf("value" to storage.get(key)))
    }

    private fun storageSet(scope: Scriptable, args: Array<out Any?>): Scriptable {
        if (!granted(PluginCapability.Storage)) return fail(scope, "not_granted")
        val key = jsString(arg(args, 0)) ?: return fail(scope, "invalid")
        val value = jsString(arg(args, 1)) ?: return fail(scope, "invalid")
        val error = storage.set(key, value)
        return if (error == null) ok(scope) else fail(scope, error)
    }

    private fun fetch(scope: Scriptable, args: Array<out Any?>): Scriptable {
        if (!granted(PluginCapability.Network)) return fail(scope, "not_granted")
        val url = jsString(arg(args, 0)) ?: return fail(scope, "invalid")
        val response = runCatching { services.fetch(url) }.getOrNull() ?: return fail(scope, "failed")
        if (!response.ok) return fail(scope, response.error ?: "failed")
        return ok(scope, mapOf("status" to response.status, "body" to response.body.take(PluginHttp.MaxBodyBytes)))
    }

    private fun setPage(scope: Scriptable, args: Array<out Any?>): Scriptable {
        if (!granted(PluginCapability.UiPage)) return fail(scope, "not_granted")
        val page = readPage(arg(args, 0))?.let(::parsePluginPage) ?: return fail(scope, "invalid")
        callbacks.onPage(page)
        return ok(scope)
    }

    private fun readPage(value: Any?): RawPluginPage? {
        val obj = value as? Scriptable ?: return null
        val items = (member(obj, "items") as? Scriptable)?.let(::readArray).orEmpty()
        return RawPluginPage(
            title = stringMember(obj, "title"),
            items = items.mapNotNull(::readItem),
        )
    }

    private fun readItem(value: Any?): RawPluginItem? {
        val obj = value as? Scriptable ?: return null
        val rows = (member(obj, "items") as? Scriptable)?.let(::readArray).orEmpty().map { row ->
            val rowObject = row as? Scriptable
            stringMember(rowObject, "title") to stringMember(rowObject, "subtitle")
        }
        return RawPluginItem(
            type = stringMember(obj, "type"),
            text = stringMember(obj, "text"),
            id = stringMember(obj, "id"),
            label = stringMember(obj, "label"),
            rows = rows,
        )
    }

    private fun readArray(value: Scriptable): List<Any?> {
        val length = (member(value, "length") as? Number)?.toInt() ?: return emptyList()
        if (length !in 0..40) return emptyList()
        return List(length) { index ->
            val item = ScriptableObject.getProperty(value, index)
            if (item == Scriptable.NOT_FOUND || item is Undefined) null else item
        }
    }

    private fun toJs(value: Any?): Any? {
        val context = Context.getCurrentContext()
        return context.toJs(scope, value)
    }

    private fun ok(scope: Scriptable, extra: Map<String, Any?> = emptyMap()): Scriptable =
        Context.getCurrentContext().result(scope, mapOf("ok" to true) + extra)

    private fun fail(scope: Scriptable, error: String): Scriptable =
        Context.getCurrentContext().result(scope, mapOf("ok" to false, "error" to error))

    private fun putFn(scope: Scriptable, target: Scriptable, name: String, block: (Array<out Any?>) -> Any?) {
        val function = object : BaseFunction() {
            override fun call(
                cx: Context,
                callScope: Scriptable,
                thisObj: Scriptable,
                args: Array<out Any>?,
            ): Any = block(args ?: emptyArray()) ?: Undefined.instance
        }
        function.parentScope = scope
        put(target, name, function)
    }

    private fun put(target: Scriptable, name: String, value: Any?) {
        ScriptableObject.putProperty(target, name, value)
    }

    private fun granted(capability: PluginCapability): Boolean = capability in grants()

    private companion object {
        const val MaxPositionMs = 86_400_000L
    }
}

internal class PluginContextFactory : ContextFactory() {
    override fun makeContext(): Context {
        val context = super.makeContext()
        // Android cannot load the classes Rhino's optimizer would write.
        context.optimizationLevel = -1
        context.instructionObserverThreshold = 10_000
        context.setClassShutter(ClassShutter { false })
        context.wrapFactory = object : WrapFactory() {
            override fun wrapAsJavaObject(
                cx: Context,
                scope: Scriptable,
                javaObject: Any,
                staticType: Class<*>?,
            ): Scriptable = throw PluginSandboxError()
        }
        return context
    }

    override fun observeInstructionCount(cx: Context, instructionCount: Int) {
        val deadline = cx.getThreadLocal(Deadline) as? Long ?: return
        if (System.nanoTime() > deadline) {
            cx.putThreadLocal(TimedOut, true)
            throw PluginTimeoutError()
        }
    }

    override fun hasFeature(cx: Context, featureIndex: Int): Boolean = when (featureIndex) {
        Context.FEATURE_ENHANCED_JAVA_ACCESS,
        Context.FEATURE_E4X,
        -> false
        else -> super.hasFeature(cx, featureIndex)
    }
}

internal class PluginTimeoutError : Error("timeout")
private class PluginSandboxError : Error("sandbox")

private const val Deadline = "echo.deadline"
private const val TimedOut = "echo.timedOut"

private fun arm(context: Context) {
    context.putThreadLocal(Deadline, System.nanoTime() + 2_000_000_000L)
    context.removeThreadLocal(TimedOut)
}

private fun disarm(context: Context) {
    context.removeThreadLocal(Deadline)
    context.removeThreadLocal(TimedOut)
}

private fun Context.result(scope: Scriptable, values: Map<String, Any?>): Scriptable =
    toJs(scope, values) as Scriptable

private fun Context.toJs(scope: Scriptable, value: Any?): Any? = when (value) {
    null -> null
    is String, is Boolean -> value
    is Number -> value.toDouble()
    is List<*> -> {
        val array = newArray(scope, value.size)
        value.forEachIndexed { index, item ->
            ScriptableObject.putProperty(array, index, toJs(scope, item))
        }
        array
    }
    is Map<*, *> -> {
        val obj = newObject(scope)
        value.forEach { (key, item) -> ScriptableObject.putProperty(obj, key.toString(), toJs(scope, item)) }
        obj
    }
    else -> null
}

private fun arg(args: Array<out Any?>, index: Int): Any? {
    val value = args.getOrNull(index) ?: return null
    return if (value is Undefined) null else value
}

private fun member(obj: Scriptable?, name: String): Any? {
    if (obj == null) return null
    val value = ScriptableObject.getProperty(obj, name)
    return if (value == Scriptable.NOT_FOUND || value is Undefined) null else value
}

private fun stringMember(obj: Scriptable?, name: String): String? = jsString(member(obj, name))

private fun jsString(value: Any?): String? {
    val unwrapped = if (value is Wrapper) value.unwrap() else value
    return if (unwrapped is CharSequence) unwrapped.toString() else null
}

private fun jsLong(value: Any?): Long? {
    val unwrapped = if (value is Wrapper) value.unwrap() else value
    return (unwrapped as? Number)?.toLong()
}

private fun String.toLogLine(): String = replace('\n', ' ').replace('\r', ' ').take(180).ifBlank { "log" }

private fun clean(error: Throwable): String {
    val line = error.message?.lineSequence()?.firstOrNull().orEmpty()
    return line.substringAfterLast('\\').substringAfterLast('/').take(180).ifBlank { "script" }
}
