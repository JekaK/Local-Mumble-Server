package ua.school.localmumble

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

internal object NetworkAddresses {
    fun localIpv4Addresses(): List<String> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces()).filter { it.isUp && !it.isLoopback }
            .sortedBy { priority(it.name) }.flatMap { network ->
                if (priority(network.name) >= 2) emptyList()
                else Collections.list(network.inetAddresses).filter {
                    it is Inet4Address && !it.isLoopbackAddress && (it.isSiteLocalAddress || it.isLinkLocalAddress)
                }.mapNotNull { it.hostAddress }
            }.distinct()
    }.getOrDefault(emptyList())
    private fun priority(name: String): Int {
        val value = name.lowercase(Locale.ROOT)
        return when {
            value.contains("ap") || value.contains("wlan") -> 0
            value.contains("eth") -> 1
            else -> 2
        }
    }
}
