package app.echo.android.connect

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object EchoLinkLanAddresses {
    /** A bounded TCP probe follows the same route as PC registration, including VPN routing. */
    fun ipv4ForPeer(host: String, port: Int): String? = Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), 3_000)
        shareableIpv4(socket.localAddress)
    }

    internal fun shareableIpv4(address: InetAddress): String? =
        address.takeIf { it is Inet4Address && !it.isLoopbackAddress && !it.isAnyLocalAddress }
            ?.hostAddress

    fun ipv4(context: Context): String? {
        val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val networks = buildList {
            manager.activeNetwork?.let(::add)
            @Suppress("DEPRECATION")
            addAll(manager.allNetworks)
        }.distinct()
        val hosts = networks.flatMap { network ->
            manager.getLinkProperties(network)?.linkAddresses.orEmpty().mapNotNull { address ->
                address.address?.hostAddress
            }
        }
        return EchoLinkCastPolicy.pickLanIpv4(hosts)
    }
}
