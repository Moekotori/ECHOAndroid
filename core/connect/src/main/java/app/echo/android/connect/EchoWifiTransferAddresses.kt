package app.echo.android.connect

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

object EchoWifiTransferAddresses {
    fun ipv4(context: Context): String? = networkAndIpv4(context)?.second?.hostAddress

    internal fun networkAndIpv4(context: Context): Pair<Network, Inet4Address>? {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return null
        val activeNetwork = manager.activeNetwork
        @Suppress("DEPRECATION")
        val networks = manager.allNetworks.toList().sortedBy { if (it == activeNetwork) 0 else 1 }
        return networks.firstNotNullOfOrNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
            if ((!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@firstNotNullOfOrNull null
            val address = manager.getLinkProperties(network)?.linkAddresses?.firstNotNullOfOrNull {
                (it.address as? Inet4Address)?.takeIf { address -> address.isSiteLocalAddress || address.isLinkLocalAddress }
            }
            address?.let { network to it }
        }
    }
}
