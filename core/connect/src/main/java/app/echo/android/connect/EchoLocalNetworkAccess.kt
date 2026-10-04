package app.echo.android.connect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Check before discovery or connecting; never prompt from process-owned background work. */
fun Context.hasEchoLocalNetworkAccess(): Boolean =
    Build.VERSION.SDK_INT < 37 || applicationInfo.targetSdkVersion < 37 ||
        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

/** Address classification never performs DNS on the UI thread. */
fun echoAddressNeedsLocalNetworkAccess(address: String): Boolean {
    val uri = runCatching { java.net.URI(if ("://" in address) address else "http://$address") }.getOrNull() ?: return false
    val host = uri.host?.lowercase()?.removeSurrounding("[", "]") ?: return false
    if (host == "localhost" || host == "::1" || host.startsWith("127.")) return false
    if (':' in host) return host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80:") || host.startsWith("ff")
    val octets = host.split('.').mapNotNull(String::toIntOrNull)
    if (octets.size == 4 && octets.all { it in 0..255 }) return (
        octets[0] == 10 || (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168) || (octets[0] == 169 && octets[1] == 254) ||
            (octets[0] == 100 && octets[1] in 64..127))
    return !host.contains('.') || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa")
}
