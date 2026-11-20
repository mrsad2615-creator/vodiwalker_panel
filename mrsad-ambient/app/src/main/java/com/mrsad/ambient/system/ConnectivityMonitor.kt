package com.mrsad.ambient.system

import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mrsad.ambient.domain.model.ConnectivityState

class ConnectivityMonitor(private val context: Context) {

    fun getCurrentState(): ConnectivityState {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btEnabled = try {
            btManager?.adapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }

        return ConnectivityState(
            wifiConnected = wifi,
            internetAvailable = internet,
            bluetoothEnabled = btEnabled
        )
    }
}
