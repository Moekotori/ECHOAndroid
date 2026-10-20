package app.echo.android.feature.player.afterglow

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/**
 * Owns the one offline WebView that runs the echosteam Jizura engine (assets/jizura/engine.html).
 * No network, no navigation, no file access beyond the bundled asset. Call from the main thread only.
 */
internal class AfterglowEngineHost(context: Context, background: Int, private val onEvent: (type: String, data: JSONObject) -> Unit) {
    private var disposed = false

    // Touches fall through to Compose, so the page keeps its own tap-to-toggle chrome.
    private class PassiveWebView(context: Context) : WebView(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
        override fun onTouchEvent(event: MotionEvent): Boolean = false
    }

    private inner class Bridge {
        @JavascriptInterface
        fun post(message: String) {
            if (message.length > 65_536) return
            val data = runCatching { JSONObject(message) }.getOrNull() ?: return
            val type = data.optString("type")
            if (!type.startsWith("echo:jizura:")) return
            // Bridge calls arrive on a WebView thread.
            view?.post { if (!disposed) onEvent(type, data) }
        }
    }

    private val bridge = Bridge()

    val view: WebView? = runCatching { create(context, background) }.getOrNull()

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun create(context: Context, background: Int): WebView = PassiveWebView(context).apply {
        setBackgroundColor(background)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = WebView.OVER_SCROLL_NEVER
        importantForAccessibility = WebView.IMPORTANT_FOR_ACCESSIBILITY_NO
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = true // file:///android_asset only; the page cannot reach other files
            allowContentAccess = false
            blockNetworkLoads = true
            setSupportZoom(false)
            builtInZoomControls = false
            setGeolocationEnabled(false)
        }
        addJavascriptInterface(bridge, "EchoJizuraHost")
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
            override fun onPageFinished(view: WebView, url: String) { if (!disposed) deliver("{\"type\":\"echo:jizura:hello\"}") }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (!disposed) onEvent("echo:jizura:error", JSONObject())
                return true
            }
        }
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d("EchoJizura", "${message.messageLevel()} ${message.message()} (${message.lineNumber()})")
                    return true
                }
            }
        }
        loadUrl("file:///android_asset/jizura/engine.html")
    }

    /** `message` is one JSON object; the shim in the page turns it into a window message event. */
    fun deliver(message: String) {
        if (disposed) return
        view?.evaluateJavascript("window.__echoDeliver&&window.__echoDeliver(${AfterglowEngineProject.quote(message)})", null)
    }

    fun pause() { if (!disposed) view?.onPause() }
    fun resume() { if (!disposed) view?.onResume() }

    fun dispose() {
        if (disposed) return
        deliver("{\"type\":\"echo:jizura:dispose\"}")
        disposed = true
        view?.apply {
            (parent as? ViewGroup)?.removeView(this)
            removeJavascriptInterface("EchoJizuraHost")
            stopLoading()
            loadUrl("about:blank")
            onPause()
            destroy()
        }
    }
}
