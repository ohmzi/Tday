package com.ohmz.tday.di

import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import org.koin.core.qualifier.Qualifier
import org.koin.ktor.ext.getKoin

// koin-ktor 4.0.x Route.inject() is broken with Ktor 3.0.x (missing RoutingKt class).
// Resolve through Route.application → Application.getKoin() which works correctly.
inline fun <reified T : Any> Route.inject(
    qualifier: Qualifier? = null,
    mode: LazyThreadSafetyMode = LazyThreadSafetyMode.SYNCHRONIZED,
): Lazy<T> = lazy(mode) { application.getKoin().get(qualifier) }
