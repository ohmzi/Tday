package com.ohmz.tday.compose.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager: ConnectivityManager? = runCatching {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }.getOrNull()

    val isOnline: Boolean
        get() = runCatching {
            val cm = connectivityManager ?: return true
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(true)

    val connectivityChanges: Flow<Boolean> = connectivityManager?.let { cm ->
        callbackFlow {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(true)
                }

                override fun onLost(network: Network) {
                    trySend(false)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    val validated = networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                    )
                    trySend(validated)
                }
            }

            try {
                cm.registerNetworkCallback(request, callback)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "registerNetworkCallback failed", e)
                channel.close()
                return@callbackFlow
            }
            trySend(isOnline)

            awaitClose {
                runCatching { cm.unregisterNetworkCallback(callback) }
            }
        }.distinctUntilChanged()
    } ?: flowOf()

    private companion object {
        const val LOG_TAG = "ConnectivityObserver"
    }
}
