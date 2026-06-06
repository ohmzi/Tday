package com.ohmz.tday.compose.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.core.data.SecureConfigStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.sentry.okhttp.SentryOkHttpInterceptor
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.TimeZone
import javax.inject.Singleton

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
    fun provideCookieManager(cookieStore: EncryptedCookieStore): CookieManager =
        CookieManager(cookieStore, CookiePolicy.ACCEPT_ORIGINAL_SERVER)

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
                // TLS trust is enforced by OkHttp's default trust manager during the
                // handshake (system/public CA validation + hostname check), so any cert
                // that reaches here is already CA-validated. We deliberately do NOT add a
                // post-handshake public-key pin: it was redundant with CA validation and
                // false-tripped on routine renewals (e.g. Let's Encrypt rotating to a new
                // key), surfacing a bogus "certificate changed" during URL validation.
                chain.proceed(updated)
            }
            .addInterceptor(SentryOkHttpInterceptor())
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
}
