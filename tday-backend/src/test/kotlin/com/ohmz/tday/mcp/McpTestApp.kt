package com.ohmz.tday.mcp

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.plugins.configureSecurity
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.routes.mcpRoutes
import com.ohmz.tday.security.AuthCachedUser
import com.ohmz.tday.security.AuthUserCache
import com.ohmz.tday.security.JwtService
import com.ohmz.tday.security.JwtServiceImpl
import com.ohmz.tday.security.SecurityEventLogger
import com.ohmz.tday.security.testAppConfig
import com.ohmz.tday.services.ApiKeyInfo
import com.ohmz.tday.services.ApiKeyScope
import com.ohmz.tday.services.GeneratedApiKey
import com.ohmz.tday.services.RecurrenceExpanderImpl
import com.ohmz.tday.services.ResolvedApiKey
import com.ohmz.tday.services.UserApiKeyService
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

const val MCP_READ_KEY = "tday_readkey_secret"
const val MCP_FULL_KEY = "tday_fullkey_secret"

val mcpJson = Json { ignoreUnknownKeys = true }

/** Stands the MCP endpoint up over [world], with the real dispatcher and expander. */
fun Application.configureMcpTestApp(world: McpTestWorld) {
    val config = testAppConfig()
    val authUserCache = AuthUserCache().apply {
        put(
            world.userId,
            AuthCachedUser(
                role = "USER",
                approvalStatus = "APPROVED",
                tokenVersion = 1,
                timeZone = world.timeZone,
            ),
        )
    }
    install(Koin) {
        modules(
            module {
                single { config }
                single<JwtService> { JwtServiceImpl(config) }
                single { authUserCache }
                single<SecurityEventLogger> { NoOpSecurityEventLogger() }
                single<UserApiKeyService> { ScopedFakeApiKeyService(world.userId) }
                single {
                    McpToolDispatcher(
                        TdayMcpService(
                            world.integrationContextService,
                            world.todoService,
                            world.floaterService,
                            world.listService,
                            world.floaterListService,
                            world.completedTodoService,
                            world.completedFloaterService,
                            RecurrenceExpanderImpl(),
                        ),
                    )
                }
            },
        )
    }
    configureSerialization()
    configureSecurity()
    routing { mcpRoutes() }
}

suspend fun HttpClient.mcp(key: String?, body: String): HttpResponse = post("/mcp") {
    if (key != null) header(HttpHeaders.Authorization, "Bearer $key")
    contentType(ContentType.Application.Json)
    setBody(body)
}

suspend fun HttpClient.callTool(key: String, name: String, arguments: String = "{}"): JsonObject =
    mcp(key, """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"$name","arguments":$arguments}}""")
        .result()

suspend fun HttpResponse.result(): JsonObject =
    mcpJson.parseToJsonElement(bodyAsText()).jsonObject.getValue("result").jsonObject

suspend fun HttpResponse.errorObject(): JsonObject =
    mcpJson.parseToJsonElement(bodyAsText()).jsonObject.getValue("error").jsonObject

/** The concatenated text of a tools/call result. */
fun JsonObject.toolText(): String =
    getValue("content").jsonArray.joinToString("\n") { it.jsonObject.getValue("text").jsonPrimitive.content }

fun JsonObject.isToolError(): Boolean = getValue("isError").jsonPrimitive.boolean

private class NoOpSecurityEventLogger : SecurityEventLogger {
    override suspend fun log(reasonCode: String, details: Map<String, Any?>) = Unit
}

private class ScopedFakeApiKeyService(private val userId: String) : UserApiKeyService {
    override suspend fun generate(
        userId: String,
        label: String?,
        scope: ApiKeyScope,
        expiresInDays: Long?,
    ): Either<AppError, GeneratedApiKey> = Either.Left(AppError.Internal("unused"))

    override suspend fun list(userId: String) = emptyList<ApiKeyInfo>().right()

    override suspend fun revokeKey(userId: String, keyId: String) = Unit.right()

    override suspend fun revoke(userId: String) = Unit.right()

    override suspend fun resolveKey(rawKey: String): ResolvedApiKey? = when (rawKey) {
        MCP_READ_KEY -> ResolvedApiKey(userId, ApiKeyScope.READ, "readkey", "Dashboard", "cret")
        MCP_FULL_KEY -> ResolvedApiKey(userId, ApiKeyScope.FULL, "fullkey", "Claude", "cret")
        else -> null
    }
}
