package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.raise.either
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.shared.model.IntegrationApiKeyDto
import com.ohmz.tday.shared.model.IntegrationCapabilitiesDto
import com.ohmz.tday.shared.model.IntegrationContextResponse
import com.ohmz.tday.shared.model.IntegrationUserDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Assembles the one-call grounding payload for external integrations.
 *
 * Composition only — every field comes from an existing service, so tenant
 * isolation and share visibility stay owned by [ListService] / [FloaterListService].
 */
interface IntegrationContextService {
    suspend fun contextFor(
        userId: String,
        timeZone: String?,
        apiKey: IntegrationApiKeyDto?,
    ): Either<AppError, IntegrationContextResponse>
}

class IntegrationContextServiceImpl(
    private val userService: UserService,
    private val listService: ListService,
    private val floaterListService: FloaterListService,
) : IntegrationContextService {

    override suspend fun contextFor(
        userId: String,
        timeZone: String?,
        apiKey: IntegrationApiKeyDto?,
    ): Either<AppError, IntegrationContextResponse> = either {
        // Three independent read-only lookups, issued together. Results are bound in the
        // original order, so the first failing read reports the same error.
        val reads = coroutineScope {
            val profileRead = async { userService.getProfile(userId) }
            val listsRead = async { listService.getAll(userId) }
            val anytimeListsRead = async { floaterListService.getAll(userId) }
            Triple(profileRead.await(), listsRead.await(), anytimeListsRead.await())
        }
        val profile = reads.first.bind()
        val lists = reads.second.bind()
        val anytimeLists = reads.third.bind()

        IntegrationContextResponse(
            apiKey = apiKey,
            user = IntegrationUserDto(
                id = profile.id,
                username = profile.username,
                name = profile.name,
                timeZone = timeZone,
            ),
            serverTime = LocalDateTime.now(ZoneOffset.UTC).format(WIRE_FORMAT),
            capabilities = IntegrationCapabilitiesDto(
                // Fail closed: only an explicitly READ-scoped key loses write access,
                // but an unreadable/absent scope is never treated as writable by accident.
                canWrite = apiKey == null || apiKey.scope == ApiKeyScope.FULL.name,
            ),
            lists = lists,
            anytimeLists = anytimeLists,
        )
    }

    private companion object {
        /** The API's wire format: a UTC wall clock with no offset, to the second. */
        val WIRE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}
