package com.ohmz.tday.compose.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.core.data.SecureConfigStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.net.CookieManager
import java.net.CookiePolicy
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.TimeZone
import javax.inject.Singleton
import okhttp3.JavaNetCookieJar
import javax.net.ssl.SSLPeerUnverifiedException

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideCookieManager(): CookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieManager: CookieManager,
        secureConfigStore: SecureConfigStore,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            // NextAuth callback responses may issue absolute redirects; keep auth flow
            // in-app and avoid jumping to unreachable localhost targets on Android.
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                val request = chain.request()
                val bypassRewrite = request.header("X-Tday-No-Rewrite") == "1"
                val baseUrl = secureConfigStore.getServerUrl()?.toHttpUrlOrNull()
                val rewrittenUrl = if (!bypassRewrite && baseUrl != null) {
                    request.url.newBuilder()
                        .scheme(baseUrl.scheme)
                        .host(baseUrl.host)
                        .port(baseUrl.port)
                        .build()
                } else {
                    request.url
                }

                val updated = request.newBuilder()
                    .url(rewrittenUrl)
                    .removeHeader("X-Tday-No-Rewrite")
                    .header("Accept", "application/json")
                    .header("X-User-Timezone", TimeZone.getDefault().id)
                    .header("X-Tday-Client", "android-compose")
                    .header("X-Tday-App-Version", BuildConfig.VERSION_NAME)
                    .header("X-Tday-Device-Id", secureConfigStore.getOrCreateDeviceId())
                    .build()
                val response = chain.proceed(updated)

                if (updated.url.isHttps) {
                    val trustKey = secureConfigStore.serverTrustKeyForUrl(updated.url.toString())
                    val serverCertificate = response.handshake
                        ?.peerCertificates
                        ?.firstOrNull() as? X509Certificate
                    if (!trustKey.isNullOrBlank() && serverCertificate != null) {
                        val observedFingerprint = certificatePublicKeyFingerprint(serverCertificate)
                        val trustedFingerprint = secureConfigStore.getTrustedServerFingerprint(trustKey)
                        if (trustedFingerprint.isNullOrBlank()) {
                            secureConfigStore.saveTrustedServerFingerprint(
                                serverTrustKey = trustKey,
                                fingerprint = observedFingerprint,
                            )
                        } else if (!trustedFingerprint.equals(observedFingerprint, ignoreCase = true)) {
                            response.close()
                            throw SSLPeerUnverifiedException(
                                "Pinned certificate mismatch for $trustKey",
                            )
                        }
                    }
                }

                response
            }
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        json: Json,
        okHttpClient: OkHttpClient,
    ): Retrofit {
        return Retrofit.Builder()
            // Placeholder base URL; runtime host is rewritten from encrypted user config.
            .baseUrl("https://placeholder.invalid/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): TdayApiService =
        retrofit.create(TdayApiService::class.java)

    private fun certificatePublicKeyFingerprint(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(certificate.publicKey.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
