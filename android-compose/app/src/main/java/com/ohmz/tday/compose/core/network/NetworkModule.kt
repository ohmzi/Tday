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
import java.util.TimeZone
import javax.inject.Singleton
import okhttp3.JavaNetCookieJar

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
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieManager: CookieManager,
        secureConfigStore: SecureConfigStore,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
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
                chain.proceed(updated)
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
}
