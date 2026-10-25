package app.echo.android.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import app.echo.android.model.error.EchoDiagnosticEvents
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-owned, opt-in capture. All file work is serialized on IO; producers never wait. */
class EchoDiagnosticRecorder(context: Context, private val scope: CoroutineScope, private val version: String) {
    private val directory = File(context.applicationContext.filesDir, "diagnostic-capture")
    private val files = DiagnosticCaptureFileStore(directory)
    private val generation = AtomicLong()
    private val messages = Channel<Message>(256)
    private val active = MutableStateFlow(false)
    val enabled = active.asStateFlow()
    private var timer: Job? = null
    @Volatile private var deadline = 0L
    @Volatile private var monotonicDeadline = 0L
    private val dropped = AtomicLong()
    private val sink = EchoDiagnosticEvents.Sink { event, value, detail ->
        val token = generation.get()
        if (active.value && !expired()) {
            if (!messages.trySend(Message.Line(token, "${System.currentTimeMillis()} ${SystemClock.elapsedRealtime()} ${event.name} $value $detail\n")).isSuccess)
                dropped.incrementAndGet()
        } else if (active.value) stop()
    }
    init {
        scope.launch(Dispatchers.IO) {
            for (message in messages) when (message) {
                is Message.Line -> runCatching { if (message.generation == generation.get()) files.append(message.text) }.onFailure { stop() }
                is Message.Export -> {
                    try {
                        ZipOutputStream(object : java.io.FilterOutputStream(message.output) { override fun close() { flush() } }).use { zip ->
                            zip.putNextEntry(ZipEntry("capture-info.txt"))
                            zip.write(("ECHO diagnostic capture\nVersion: $version\nAndroid API: ${Build.VERSION.SDK_INT}\n" +
                                "Device: ${Build.MANUFACTURER} ${Build.MODEL}\nCapture start epochMs: ${deadline.takeIf { it > 0 }?.minus(DurationMs) ?: 0}\nEnabled: ${active.value && !expired()}\nDropped events: ${dropped.get()}\n" +
                                "Columns: epochMs elapsedMs event value detail\n" +
                                "UsbTransport=queued/min frames; UsbSupply=gap count/frames; UsbAudioPackets=errors/URBs; UsbFeedbackPackets=errors/URBs; UsbUrbs=submit/reap failures; UsbFeedbackClock=invalid count/error code.\nNo media names, file paths or account data are recorded.\n").toByteArray())
                            zip.closeEntry()
                            files.files().forEach { file ->
                                zip.putNextEntry(ZipEntry(file.name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                            }
                        }
                        message.done.complete(Unit)
                    } catch (error: Exception) { message.done.completeExceptionally(error) }
                }
                is Message.Clear -> {
                    try { files.clear(); dropped.set(0); message.done.complete(Unit) }
                    catch (error: Exception) { message.done.completeExceptionally(error) }
                }
            }
        }
    }
    @Synchronized fun start() {
        if (active.value) return
        deadline = System.currentTimeMillis() + DurationMs
        monotonicDeadline = SystemClock.elapsedRealtime() + DurationMs
        active.value = true
        messages.trySend(Message.Line(generation.get(), "# Capture version=${version.take(80).replace('\n', ' ').replace('\r', ' ')} API=${Build.VERSION.SDK_INT}\n"))
        EchoDiagnosticEvents.install(sink)
        sink.record(EchoDiagnosticEvents.Event.CaptureStarted, 0, 0)
        timer?.cancel()
        val token = monotonicDeadline
        timer = scope.launch { delay(DurationMs); synchronized(this@EchoDiagnosticRecorder) { if (monotonicDeadline == token) stop() } }
    }
    @Synchronized fun stop() {
        if (active.value) messages.trySend(Message.Line(generation.get(), "${System.currentTimeMillis()} ${SystemClock.elapsedRealtime()} CaptureStopped 0 0\n"))
        active.value = false; EchoDiagnosticEvents.install(null); timer?.cancel(); timer = null
    }
    private fun expired() = DiagnosticCapturePolicy.expired(System.currentTimeMillis(), SystemClock.elapsedRealtime(), deadline, monotonicDeadline)
    suspend fun export(output: OutputStream) {
        if (active.value && expired()) stop()
        val done = CompletableDeferred<Unit>(); messages.send(Message.Export(output, done)); done.await()
    }
    suspend fun clear() {
        stop(); generation.incrementAndGet(); val done = CompletableDeferred<Unit>(); messages.send(Message.Clear(done)); done.await()
    }
    private sealed interface Message {
        data class Line(val generation: Long, val text: String): Message
        data class Export(val output: OutputStream, val done: CompletableDeferred<Unit>): Message
        data class Clear(val done: CompletableDeferred<Unit>): Message
    }
    companion object { const val SegmentBytes = DiagnosticCapturePolicy.SegmentBytes; const val DurationMs = DiagnosticCapturePolicy.DurationMs }
}
