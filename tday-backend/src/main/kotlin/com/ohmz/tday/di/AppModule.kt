package com.ohmz.tday.di

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.config.DatabaseConfig
import com.ohmz.tday.security.*
import com.ohmz.tday.services.*
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
    single<SessionControl> { SessionControlImpl(get()) }
}

val serviceModule = module {
    single<CacheService> { CacheServiceImpl() }
    single<TodoService> { TodoServiceImpl(get(), get()) }
    single<ListService> { ListServiceImpl(get()) }
    single<NoteService> { NoteServiceImpl(get()) }
    single<UserService> { UserServiceImpl(get()) }
    single<CompletedTodoService> { CompletedTodoServiceImpl(get(), get()) }
    single<PreferencesService> { PreferencesServiceImpl() }
    single<AppConfigService> { AppConfigServiceImpl() }
    single<TodoSummaryService> { TodoSummaryServiceImpl(get()) }
    single<TodoNlpService> { TodoNlpServiceImpl() }
    single<RealtimeService> { RealtimeServiceImpl() }
}
