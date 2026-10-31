package com.tryniecki.qumi.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Follows the default network, which every new socket uses; null means there is none. */
class NetworkMonitor(context: Context, private val onChanged: () -> Unit) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _network = MutableStateFlow(connectivity.activeNetwork?.networkHandle)
    val network: StateFlow<Long?> = _network.asStateFlow()

    init {
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update(network.networkHandle)
            override fun onLost(network: Network) = update(null)
        })
    }

    private fun update(handle: Long?) {
        if (_network.getAndUpdate { handle } != handle) onChanged()
    }
}
