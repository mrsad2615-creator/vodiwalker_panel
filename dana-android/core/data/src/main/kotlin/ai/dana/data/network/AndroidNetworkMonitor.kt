package ai.dana.data.network

import ai.dana.domain.NetworkMonitor
import ai.dana.domain.NetworkStatus
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

internal class AndroidNetworkMonitor(private val context: Context) : NetworkMonitor {
    override val status: Flow<NetworkStatus> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            trySend(NetworkStatus.UNAVAILABLE)
            close()
            return@callbackFlow
        }

        // Signal a refresh; query the active network outside platform callback methods.
        val signals = Channel<Unit>(Channel.CONFLATED)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { signals.trySend(Unit) }
            override fun onLost(network: Network) { signals.trySend(Unit) }
            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) { signals.trySend(Unit) }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                signals.trySend(Unit)
            }
        }
        try {
            manager.registerDefaultNetworkCallback(callback)
        } catch (_: RuntimeException) {
            signals.close()
            trySend(NetworkStatus.UNAVAILABLE)
            close()
            return@callbackFlow
        }
        signals.trySend(Unit)
        launch {
            for (ignored in signals) trySend(readStatus(manager))
        }
        awaitClose {
            signals.close()
            try {
                manager.unregisterNetworkCallback(callback)
            } catch (_: IllegalArgumentException) {
                // The platform no longer has the registration.
            }
        }
    }.flowOn(Dispatchers.IO).conflate().distinctUntilChanged()

    private fun readStatus(manager: ConnectivityManager): NetworkStatus = try {
        val active = manager.activeNetwork
        if (active == null) {
            NetworkStatus.NO_DEFAULT_NETWORK
        } else {
            val capabilities = manager.getNetworkCapabilities(active)
            if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) NetworkStatus.INTERNET_VALIDATED else NetworkStatus.INTERNET_UNVALIDATED
        }
    } catch (_: RuntimeException) {
        NetworkStatus.UNAVAILABLE
    }
}
