package eu.kanade.tachiyomi.ui.player.dlna

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.MulticastSocket

internal fun Context.dlnaControlPoint(): DlnaControlPoint {
    val connectivity = getSystemService(ConnectivityManager::class.java)
        ?: throw IOException("No local network service")

    // A Wi-Fi link without internet can coexist with a cellular default network.
    @Suppress("DEPRECATION")
    val networks = (listOfNotNull(connectivity.activeNetwork) + connectivity.allNetworks).distinct()
    for (network in networks) {
        val capabilities = connectivity.getNetworkCapabilities(network) ?: continue
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) {
            continue
        }
        val local = connectivity.getLinkProperties(network)?.linkAddresses?.firstOrNull {
            it.address is Inet4Address && !it.address.isLoopbackAddress && it.prefixLength in 1..32
        } ?: continue
        val discovery = DlnaDiscovery(
            openSocket = {
                MulticastSocket(null).also { socket ->
                    try {
                        network.bindSocket(socket)
                        socket.bind(InetSocketAddress(local.address, 0))
                        socket.timeToLive = 2
                    } catch (error: Exception) {
                        socket.close()
                        throw error
                    }
                }
            },
            isLocalDevice = { sender ->
                !sender.isLoopbackAddress && !sender.isAnyLocalAddress && !sender.isMulticastAddress &&
                    sender != local.address && sameSubnet(local.address.address, sender.address, local.prefixLength)
            },
        )
        val client = OkHttpClient.Builder().socketFactory(network.socketFactory).build()
        return DlnaControlPoint(discovery, client)
    }
    throw IOException("No local IPv4 network")
}

internal fun sameSubnet(local: ByteArray, sender: ByteArray, prefixLength: Int): Boolean =
    local.indices.all { index ->
        val bits = (prefixLength - index * 8).coerceIn(0, 8)
        val mask = 0xff and (0xff shl (8 - bits))
        (local[index].toInt() and mask) == (sender[index].toInt() and mask)
    }
