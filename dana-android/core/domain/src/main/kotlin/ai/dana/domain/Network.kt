package ai.dana.domain

import kotlinx.coroutines.flow.Flow

enum class NetworkStatus {
    CHECKING,
    NO_DEFAULT_NETWORK,
    INTERNET_UNVALIDATED,
    INTERNET_VALIDATED,
    UNAVAILABLE,
}

interface NetworkMonitor {
    val status: Flow<NetworkStatus>
}

interface PrivacyPreferences {
    /** True by default. Future transports must not use either WAN or LAN in this mode. */
    val offlineMode: Flow<Boolean>
    suspend fun setOfflineMode(enabled: Boolean)
}
