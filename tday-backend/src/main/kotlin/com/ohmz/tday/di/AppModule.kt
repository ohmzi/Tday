package com.ohmz.tday.di

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.config.DatabaseConfig
import com.ohmz.tday.security.AuthThrottle
import com.ohmz.tday.security.AuthThrottleImpl
import com.ohmz.tday.security.AuthUserCache
import com.ohmz.tday.security.CaptchaService
import com.ohmz.tday.security.CaptchaServiceImpl
import com.ohmz.tday.security.ClientSignals
import com.ohmz.tday.security.ClientSignalsImpl
import com.ohmz.tday.security.CredentialEnvelope
import com.ohmz.tday.security.CredentialEnvelopeImpl
import com.ohmz.tday.security.FieldEncryption
import com.ohmz.tday.security.FieldEncryptionImpl
import com.ohmz.tday.security.InMemoryRequestRateLimiter
import com.ohmz.tday.security.JwtService
import com.ohmz.tday.security.JwtServiceImpl
import com.ohmz.tday.security.PasswordProof
import com.ohmz.tday.security.PasswordProofImpl
import com.ohmz.tday.security.PasswordService
import com.ohmz.tday.security.PasswordServiceImpl
import com.ohmz.tday.security.RequestRateLimiter
import com.ohmz.tday.security.SecurityEventLogger
import com.ohmz.tday.security.SecurityEventLoggerImpl
import com.ohmz.tday.security.SessionControl
import com.ohmz.tday.security.SessionControlImpl
import com.ohmz.tday.services.AdminService
import com.ohmz.tday.services.AdminServiceImpl
import com.ohmz.tday.services.AppConfigService
import com.ohmz.tday.services.AppConfigServiceImpl
import com.ohmz.tday.services.CacheService
import com.ohmz.tday.services.CacheServiceImpl
import com.ohmz.tday.services.CompletedFloaterService
import com.ohmz.tday.services.CompletedFloaterServiceImpl
import com.ohmz.tday.services.CompletedTodoService
import com.ohmz.tday.services.CompletedTodoServiceImpl
import com.ohmz.tday.services.FloaterListService
import com.ohmz.tday.services.FloaterListServiceImpl
import com.ohmz.tday.services.FloaterService
import com.ohmz.tday.services.FloaterServiceImpl
import com.ohmz.tday.services.ListService
import com.ohmz.tday.services.ListServiceImpl
import com.ohmz.tday.services.PreferencesService
import com.ohmz.tday.services.PreferencesServiceImpl
import com.ohmz.tday.services.PushNotificationService
import com.ohmz.tday.services.PushNotificationServiceImpl
import com.ohmz.tday.services.RealtimeService
import com.ohmz.tday.services.RealtimeServiceImpl
import com.ohmz.tday.services.SecurityQuestionService
import com.ohmz.tday.services.SecurityQuestionServiceImpl
import com.ohmz.tday.services.TodoNlpService
import com.ohmz.tday.services.TodoNlpServiceImpl
import com.ohmz.tday.services.TodoService
import com.ohmz.tday.services.TodoServiceImpl
import com.ohmz.tday.services.TodoSummaryService
import com.ohmz.tday.services.TodoSummaryServiceImpl
import com.ohmz.tday.services.UserApiKeyService
import com.ohmz.tday.services.UserApiKeyServiceImpl
import com.ohmz.tday.services.UserService
import com.ohmz.tday.services.UserServiceImpl
import org.koin.dsl.module

fun configModule(config: AppConfig) = module {
    single { config }
    single { DatabaseConfig(get()) }
}

val securityModule = module {
    single<ClientSignals> { ClientSignalsImpl(get()) }
    single<SecurityEventLogger> { SecurityEventLoggerImpl() }
    single<JwtService> { JwtServiceImpl(get()) }
    single<FieldEncryption> { FieldEncryptionImpl(get()) }
    single<PasswordService> { PasswordServiceImpl(get()) }
    single<PasswordProof> { PasswordProofImpl(get(), get()) }
    single<AuthThrottle> { AuthThrottleImpl(get(), get(), get()) }
    single<CaptchaService> { CaptchaServiceImpl(get(), get()) }
    single<CredentialEnvelope> { CredentialEnvelopeImpl(get()) }
    single<RequestRateLimiter> { InMemoryRequestRateLimiter(get(), get()) }
    single { AuthUserCache() }
    single<SessionControl> { SessionControlImpl(get(), get()) }
}

val serviceModule = module {
    single<CacheService> { CacheServiceImpl() }
    single<TodoService> { TodoServiceImpl(get(), get()) }
    single<FloaterService> { FloaterServiceImpl(get(), get()) }
    single<ListService> { ListServiceImpl(get()) }
    single<FloaterListService> { FloaterListServiceImpl(get()) }
    single<UserService> { UserServiceImpl(get()) }
    single<SecurityQuestionService> { SecurityQuestionServiceImpl(get(), get(), get(), get()) }
    single<CompletedTodoService> { CompletedTodoServiceImpl(get(), get()) }
    single<CompletedFloaterService> { CompletedFloaterServiceImpl(get(), get()) }
    single<PreferencesService> { PreferencesServiceImpl() }
    single<AppConfigService> { AppConfigServiceImpl() }
    single<TodoSummaryService> { TodoSummaryServiceImpl(get()) }
    single<TodoNlpService> { TodoNlpServiceImpl() }
    single<RealtimeService> { RealtimeServiceImpl() }
    single<AdminService> { AdminServiceImpl(get(), get()) }
    single<PushNotificationService> { PushNotificationServiceImpl(get()) }
    single<UserApiKeyService> { UserApiKeyServiceImpl(get()) }
}
