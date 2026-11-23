package com.music.amber.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

object NetworkResume {
    @Volatile
    private var registered = false

    @Volatile
    private var online = false

    @Volatile
    private var current: Network? = null

    fun register(context: Context) {
        if (registered) return
        val app = context.applicationContext
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return
        registered = true
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    val arrived = validated && (!online || network != current)
                    online = validated
                    current = network
                    if (arrived) Downloads.resumeWaitingSoon(app)
                }

                override fun onLost(network: Network) {
                    if (network == current) {
                        online = false
                        current = null
                    }
                }
            })
        }.onFailure { registered = false }
    }

    fun isOnline(context: Context): Boolean {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
