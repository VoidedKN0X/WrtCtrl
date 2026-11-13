package com.vknxworks.wrtctrl.data.router

import android.content.Context
import android.net.ConnectivityManager

/**
 * The phone's own IP addresses on every network it's connected to (Wi-Fi, Ethernet, a VPN), from Android's
 * link properties. Uses the network state permission WrtCtrl already has; nothing is sent anywhere. Used to
 * recognise the phone in the router's client list (see [ThisPhone]).
 */
object PhoneAddresses {
    @Suppress("DEPRECATION")
    fun current(context: Context): Set<String> {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return emptySet()
        return runCatching {
            cm.allNetworks.flatMap { n ->
                cm.getLinkProperties(n)?.linkAddresses.orEmpty().mapNotNull { it.address?.hostAddress }
            }.map(ThisPhone::normaliseIp).filter { it.isNotEmpty() }.toSet()
        }.getOrDefault(emptySet())
    }
}
