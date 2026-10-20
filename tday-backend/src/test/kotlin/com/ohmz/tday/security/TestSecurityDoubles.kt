package com.ohmz.tday.security

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.AuthenticatedUser
import com.ohmz.tday.domain.requireAdminAccess
import com.ohmz.tday.models.response.AbuseBlockResponse
import com.ohmz.tday.models.response.SecurityAlertResponse
import com.ohmz.tday.services.SecurityAlertService
import com.ohmz.tday.services.SecurityAlertType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.ApplicationRequest

/**
 * Hand-written stand-ins for the two DB-backed security collaborators. This repo has no test
 * database, so every route test wires these instead.
 */
class FakeAbuseGuard(
    var verdict: AbuseBlockVerdict = AbuseBlockVerdict.allowed,
    var blocks: MutableList<AbuseBlockResponse> = mutableListOf(),
    var clearResult: Either<AppError, String> = "block cleared".right(),
) : AbuseGuard {
    val checkedScopes = mutableListOf<AbuseScope>()
    val signals = mutableListOf<Pair<AbuseScope, AbuseSignal>>()
    var authPressureCleared = 0
    var clearedBlockIds = mutableListOf<String>()

    override suspend fun blockVerdict(scope: AbuseScope, call: ApplicationCall): AbuseBlockVerdict {
        checkedScopes += scope
        return verdict
    }

    override suspend fun recordSignal(scope: AbuseScope, signal: AbuseSignal, request: ApplicationRequest) {
        signals += scope to signal
    }

    override suspend fun clearAuthPressure(request: ApplicationRequest) {
        authPressureCleared++
    }

    // The admin check lives in the service in this codebase, so the fakes keep it: that is what
    // makes a non-admin's 403 an assertable property of the wiring.
    override suspend fun listActiveBlocks(admin: AuthenticatedUser): Either<AppError, List<AbuseBlockResponse>> = either {
        admin.requireAdminAccess().bind()
        blocks.toList()
    }

    override suspend fun clearBlock(blockId: String, admin: AuthenticatedUser): Either<AppError, String> = either {
        admin.requireAdminAccess().bind()
        clearedBlockIds += blockId
        clearResult.bind()
    }
}

class FakeSecurityAlertService(
    var alerts: MutableList<SecurityAlertResponse> = mutableListOf(),
) : SecurityAlertService {
    val raised = mutableListOf<Pair<SecurityAlertType, String>>()

    override fun raise(type: SecurityAlertType, detail: String) {
        raised += type to detail
    }

    override suspend fun listRecent(admin: AuthenticatedUser): Either<AppError, List<SecurityAlertResponse>> = either {
        admin.requireAdminAccess().bind()
        alerts.toList()
    }
}
