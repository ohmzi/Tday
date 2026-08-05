package com.ohmz.tday.compose.core.data.server

import android.util.Log
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.core.data.AppDataMode
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.ServerProbeException
import com.ohmz.tday.compose.core.data.extractApiErrorMessage
import com.ohmz.tday.compose.core.network.ServerTrustManager
import com.ohmz.tday.compose.core.network.isPrivateNetworkHost
import com.ohmz.tday.compose.core.network.TdayApiService
import com.ohmz.tday.compose.core.security.ProbeDecryptor
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

@Singleton
class ServerConfigRepository @Inject constructor(
    private val api: TdayApiService,
    private val secureConfigStore: SecureConfigStore,
    private val serverTrustManager: ServerTrustManager,
) {
    fun getAppDataMode(): AppDataMode = secureConfigStore.getAppDataMode()

    fun isLocalMode(): Boolean = secureConfigStore.isLocalMode()

    fun hasServerConfigured(): Boolean = secureConfigStore.hasServerUrl()

    fun getServerUrl(): String? = secureConfigStore.getServerUrl()

    fun enableLocalMode() {
        secureConfigStore.clearServerUrl()
        secureConfigStore.clearCachedSessionUser()
        secureConfigStore.clearLastUsername()
        secureConfigStore.setAppDataMode(AppDataMode.LOCAL)
    }

    data class ProbeResult(
        val serverUrl: String,
        val versionCheck: VersionCheckResult,
        val backendVersion: String?,
    )

    suspend fun saveServerUrl(rawUrl: String): Result<String> =
        probeAndSave(rawUrl).map { it.serverUrl }

    suspend fun probeAndSave(rawUrl: String): Result<ProbeResult> = runCatching {
        val normalizedServerUrl = secureConfigStore.normalizeServerUrl(rawUrl)
            ?: throw ServerProbeException.InvalidUrl()
        val parsedServerUrl = normalizedServerUrl.toHttpUrlOrNull()
            ?: throw ServerProbeException.InvalidUrl()

        ensureSecureTransport(parsedServerUrl)

        val probeUrl = parsedServerUrl.newBuilder()
            .encodedPath(PROBE_PATH)
            .query(null)
            .fragment(null)
            .build()
            .toString()

        val probeResponse = try {
            withTimeout(PROBE_TIMEOUT_MS) {
                api.probeServer(probeUrl = probeUrl)
            }
        } catch (error: Exception) {
            throw describeTrustFailure(parsedServerUrl, error)
        }

        if (!probeResponse.isSuccessful) {
            throw IllegalStateException(
                extractApiErrorMessage(
                    probeResponse,
                    "Could not verify server. Check URL and try again.",
                ),
            )
        }

        val probeBody = probeResponse.body()
            ?: throw ServerProbeException.NotTdayServer()

        validateProbeContract(probeBody)

        val saved = secureConfigStore.saveServerUrl(
            rawUrl = normalizedServerUrl,
            persist = true,
        ).getOrThrow()
        secureConfigStore.clearOfflineSyncState()

        val compatibility = probeBody.encryptedCompatibility?.let { ProbeDecryptor.decrypt(it) }
        val versionCheck = checkVersionCompatibility(compatibility)

        ProbeResult(
            serverUrl = saved,
            versionCheck = versionCheck,
            backendVersion = compatibility?.appVersion ?: probeBody.appVersion,
        )
    }

    data class VersionRecheckResult(
        val versionCheck: VersionCheckResult,
        val serverAppVersion: String?,
    )

    suspend fun recheckVersion(): VersionRecheckResult {
        val serverUrl = getServerUrl()
            ?: return VersionRecheckResult(VersionCheckResult.Compatible, null)
        val parsedServerUrl = serverUrl.toHttpUrlOrNull()
            ?: return VersionRecheckResult(VersionCheckResult.Compatible, null)
        val probeUrl = parsedServerUrl.newBuilder()
            .encodedPath(PROBE_PATH)
            .query(null)
            .fragment(null)
            .build()
            .toString()

        val probeResponse = withTimeout(PROBE_TIMEOUT_MS) {
            api.probeServer(probeUrl = probeUrl)
        }

        val body = probeResponse.body()
            ?: throw IllegalStateException("Empty probe response during version recheck")
        val compatibility = body.encryptedCompatibility?.let { ProbeDecryptor.decrypt(it) }
        return VersionRecheckResult(
            versionCheck = checkVersionCompatibility(compatibility),
            serverAppVersion = compatibility?.appVersion ?: body.appVersion,
        )
    }

    fun resetTrustedServer(rawUrl: String): Result<Unit> {
        return secureConfigStore.clearTrustedServerFingerprintForUrl(rawUrl)
    }

    /**
     * Probes again with a one-shot authorisation to pin [fingerprint] — the value the user just
     * saw and confirmed. The authorisation names the expected certificate, so one swapped between
     * the prompt and this retry is still refused.
     */
    suspend fun confirmServerTrust(rawUrl: String, fingerprint: String): Result<ProbeResult> {
        val serverTrustKey = secureConfigStore.serverTrustKeyForUrl(rawUrl)
            ?: return Result.failure(ServerProbeException.InvalidUrl())

        serverTrustManager.allowEnrollment(serverTrustKey, fingerprint)
        val result = probeAndSave(rawUrl)
        if (result.isFailure) {
            serverTrustManager.cancelEnrollment(serverTrustKey)
        }
        return result
    }

    private fun validateProbeContract(probeBody: com.ohmz.tday.compose.core.model.MobileProbeResponse) {
        val serviceOk = probeBody.service.equals("tday", ignoreCase = true)
        val versionOk = probeBody.version == "1"

        if (serviceOk && versionOk) return

        Log.w(
            LOG_TAG,
            "probe_failed_contract service=${probeBody.service} version=${probeBody.version} probe=${probeBody.probe}",
        )
        throw ServerProbeException.NotTdayServer()
    }

    /**
     * Turns a refused handshake into the typed error the setup screen acts on: a changed
     * certificate (stored pin did not match) or an unrecognised one, carrying the fingerprint the
     * user has to confirm. Anything else — timeouts, DNS, plain connection failures — is rethrown
     * untouched.
     */
    private fun describeTrustFailure(serverUrl: HttpUrl, error: Exception): Exception {
        if (serverUrl.scheme != "https") return error
        if (!error.isTlsFailure()) return error

        val serverTrustKey = secureConfigStore.serverTrustKeyForUrl(serverUrl.toString())
            ?: return error

        if (serverTrustManager.consumeMismatch(serverTrustKey)) {
            return ServerProbeException.CertificateChanged(serverTrustKey)
        }

        val offeredFingerprint = serverTrustManager.consumeUnknownFingerprint(serverTrustKey)
            ?: return error

        return ServerProbeException.CertificateUntrusted(serverTrustKey, offeredFingerprint)
    }

    /// Handshake refusals surface as an SSLException, sometimes wrapped by OkHttp's route retry.
    private fun Throwable.isTlsFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SSLException) return true
            current = current.cause?.takeIf { it !== current }
        }
        return false
    }

    private fun ensureSecureTransport(serverUrl: HttpUrl) {
        if (serverUrl.scheme == "https") return
        // Shares isPrivateNetworkHost with the trust manager on purpose: "is this host reachable
        // only from the LAN?" now gates both cleartext and certificate enrollment, and a second
        // copy of the rule is how the two drift apart.
        if (BuildConfig.DEBUG && isPrivateNetworkHost(serverUrl.host)) return
        throw ServerProbeException.InsecureTransport()
    }

    private companion object {
        const val LOG_TAG = "ServerConfigRepo"
        const val PROBE_TIMEOUT_MS = 7_000L
        const val PROBE_PATH = "/api/mobile/probe"
    }
}
