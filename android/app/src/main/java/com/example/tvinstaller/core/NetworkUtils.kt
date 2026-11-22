package com.example.tvinstaller.core

import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

object NetworkUtils {
    data class LanAddress(val interfaceName: String, val ip: String)

    /** Site-local IPv4 addresses of this phone, Wi-Fi first. */
    fun lanAddresses(): List<LanAddress> {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        return interfaces.flatMap { network ->
            if (!network.isUp || network.isLoopback) return@flatMap emptyList()
            network.inetAddresses.toList()
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                .mapNotNull { address -> address.hostAddress?.let { LanAddress(network.name, it) } }
        }
    }

    /** The address the TV should use to reach this phone. */
    fun preferredLanIp(): String? = lanAddresses()
        .sortedByDescending { rank(it.interfaceName) }
        .firstOrNull()
        ?.ip

    private fun rank(interfaceName: String): Int = when {
        interfaceName.startsWith("wlan") -> 3
        interfaceName.startsWith("ap") || interfaceName.startsWith("swlan") -> 2
        interfaceName.startsWith("eth") -> 1
        else -> 0
    }

    /** Quick TCP probe used before opening the T-Cast session. */
    fun isPortOpen(host: String, port: Int, timeoutMs: Int = 2_500): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            true
        }
    } catch (_: Exception) {
        false
    }
}
