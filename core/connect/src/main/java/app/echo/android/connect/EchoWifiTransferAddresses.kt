package app.echo.android.connect

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

object EchoWifiTransferAddresses {
    fun ipv4(context: Context): String? {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        val networks = manager.allNetworks.toList().sortedBy { if (it == manager.activeNetwork) 0 else 1 }
        return networks.firstNotNullOfOrNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
            if ((!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@firstNotNullOfOrNull null
            manager.getLinkProperties(network)?.linkAddresses?.firstNotNullOfOrNull {
                val address = it.address
                address.hostAddress?.takeIf { address is Inet4Address && (address.isSiteLocalAddress || address.isLinkLocalAddress) }
            }
        }
    }
}
