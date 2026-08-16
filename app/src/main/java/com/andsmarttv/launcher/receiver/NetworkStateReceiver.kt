package com.andsmarttv.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

enum class NetworkType {
    NONE,
    WIFI_LOW,
    WIFI_MEDIUM,
    WIFI_HIGH,
    WIFI_FULL,
    ETHERNET
}

/**
 * Monitors WiFi state, signal level, Ethernet connection, and VPN transport in real-time.
 */
class NetworkStateReceiver(private val onNetworkChanged: (NetworkType, Boolean) -> Unit) :
    BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let { ctx ->
            onNetworkChanged.invoke(getNetworkState(ctx), isVpnConnected(ctx))
        }
    }

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        context.registerReceiver(this, filter)
        onNetworkChanged.invoke(getNetworkState(context), isVpnConnected(context))
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
        } catch (e: Exception) {
            // Ignored
        }
    }

    companion object {
        fun isVpnConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
                return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } else {
                val networks = cm.allNetworks
                for (network in networks) {
                    val caps = cm.getNetworkCapabilities(network)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        return true
                    }
                }
            }
            return false
        }

        fun getNetworkState(context: Context): NetworkType {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkType.NONE
            val activeNetwork: NetworkInfo? = cm.activeNetworkInfo

            if (activeNetwork == null || !activeNetwork.isConnected) {
                return NetworkType.NONE
            }

            if (activeNetwork.type == ConnectivityManager.TYPE_ETHERNET) {
                return NetworkType.ETHERNET
            }

            if (activeNetwork.type == ConnectivityManager.TYPE_WIFI) {
                val wifiManager =
                    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val wifiInfo: WifiInfo? = wifiManager?.connectionInfo
                val level = if (wifiInfo != null) {
                    WifiManager.calculateSignalLevel(wifiInfo.rssi, 4)
                } else {
                    2
                }

                return when (level) {
                    0 -> NetworkType.WIFI_LOW
                    1 -> NetworkType.WIFI_MEDIUM
                    2 -> NetworkType.WIFI_HIGH
                    else -> NetworkType.WIFI_FULL
                }
            }

            return NetworkType.NONE
        }
    }
}
