package app.echo.android.connect

import android.content.Context
import android.net.ConnectivityManager

object EchoLinkLanAddresses {
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
