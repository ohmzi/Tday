package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.ohmz.tday.db.enums.ApprovalStatus
import com.ohmz.tday.db.enums.UserRole
import com.ohmz.tday.db.tables.*
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.AuthenticatedUser
import com.ohmz.tday.domain.requireAdminAccess
import com.ohmz.tday.models.response.AdminUserResponse
import com.ohmz.tday.security.PasswordService
import com.ohmz.tday.security.SessionControl
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.random.asKotlinRandom

/** How long an unauthenticated "please reset my password" request stays visible to the admin. */
internal const val ADMIN_RESET_REQUEST_TTL_DAYS = 7L

/** True when [requestedAt] is older than [ADMIN_RESET_REQUEST_TTL_DAYS]. Null timestamps never expire. */
internal fun isResetRequestExpired(
    requestedAt: LocalDateTime?,
    now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
): Boolean = requestedAt != null && requestedAt.isBefore(now.minusDays(ADMIN_RESET_REQUEST_TTL_DAYS))

/**
 * Every column that points at `"User".id`, in the order a purge has to clear them.
 *
 * Most of these references are ON DELETE RESTRICT, so one leftover child row rolls the entire
 * delete back and the admin panel reports a 500: a missing `push_subscriptions` entry in this
 * list is exactly what made every user who had ever enabled notifications undeletable. Keeping
 * the set declared here (rather than spelled out inside the purge) is what lets a test assert
 * that no reference has been left out.
 *
 * The handful that are CASCADE today are cleared explicitly anyway. Which rule a constraint
 * actually carries depends on whether Exposed reconciles that table (see "Foreign Keys: Flyway
 * Writes Them, Exposed Owns Them" in docs/DATA_MODEL.md), and this list is not the place to
 * depend on that answer.
 *
 * Order matters: `completedtodo`/`todos` reference `project`, and `completedfloaters`/`floaters`
 * reference `floaterproject`, so those rows go before the lists that own them.
 *
 * `Users.approvedById` is the one reference deliberately absent — it is a nullable
 * self-reference, cleared rather than deleted, so the approving admin can still be removed.
 */
internal val USER_OWNED_CHILD_COLUMNS: List<Column<String>> = listOf(
    CompletedTodos.userID,
    CompletedFloaters.userID,
    Files.userID,
    Todos.userID,
    Floaters.userID,
    Lists.userID,
    FloaterLists.userID,
    UserPreferences.userID,
    UserSecurityQuestions.userID,
    Accounts.userId,
    PushSubscriptions.userID,
    UserApiKeys.userID,
    CalendarFeedTokens.userID,
    WebhookSubscriptions.userID,
)

interface AdminService {
    suspend fun listUsers(admin: AuthenticatedUser): Either<AppError, List<AdminUserResponse>>
    suspend fun approveUser(targetId: String, admin: AuthenticatedUser): Either<AppError, String>
    suspend fun deleteUser(targetId: String, admin: AuthenticatedUser): Either<AppError, String>
    suspend fun rejectUser(targetId: String, admin: AuthenticatedUser): Either<AppError, String>
    suspend fun resetPassword(targetId: String, admin: AuthenticatedUser): Either<AppError, String>
    suspend fun clearResetRequest(targetId: String, admin: AuthenticatedUser): Either<AppError, String>
}

class AdminServiceImpl(
    private val passwordService: PasswordService,
    private val sessionControl: SessionControl,
) : AdminService {

    override suspend fun listUsers(admin: AuthenticatedUser): Either<AppError, List<AdminUserResponse>> = either {
        admin.requireAdminAccess().bind()
        newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll()
                .orderBy(
                    Users.pendingAdminReset to SortOrder.DESC,
                    Users.approvalStatus to SortOrder.DESC,
                    Users.createdAt to SortOrder.DESC,
                )
                .map { row ->
                    // Anyone on the internet can raise this flag for any username, so a stale
                    // request stops being reported after ADMIN_RESET_REQUEST_TTL_DAYS rather than
                    // pinning that user to the top of the list forever.
                    val requestedAt = row[Users.adminResetRequestedAt]
                    val stillPending = row[Users.pendingAdminReset] && !isResetRequestExpired(requestedAt)
                    AdminUserResponse(
                        id = row[Users.id],
                        name = row[Users.name],
                        username = row[Users.username],
                        role = row[Users.role].name,
                        approvalStatus = row[Users.approvalStatus].name,
                        createdAt = row[Users.createdAt].toString(),
                        approvedAt = row[Users.approvedAt]?.toString(),
                        pendingAdminReset = stillPending,
                        adminResetRequestedAt = requestedAt?.toString().takeIf { stillPending },
                    )
                }
        }
    }

    override suspend fun approveUser(targetId: String, admin: AuthenticatedUser): Either<AppError, String> = either {
        admin.requireAdminAccess().bind()

        val target = newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll().where { Users.id eq targetId }.firstOrNull()
        } ?: raise(AppError.NotFound("user not found"))

        if (target[Users.approvalStatus] == ApprovalStatus.APPROVED) {
            return@either "user is already approved"
        }

        newSuspendedTransaction(Dispatchers.IO) {
            Users.update({ Users.id eq targetId }) {
                it[Users.approvalStatus] = ApprovalStatus.APPROVED
                it[Users.approvedAt] = LocalDateTime.now(ZoneOffset.UTC)
                it[Users.approvedById] = admin.id
                it[Users.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
        }
        "user approved"
    }

    override suspend fun deleteUser(targetId: String, admin: AuthenticatedUser): Either<AppError, String> = either {
        admin.requireAdminAccess().bind()

        if (targetId == admin.id) raise(AppError.BadRequest("you cannot delete your own account"))

        newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll().where { Users.id eq targetId }.firstOrNull()
        } ?: raise(AppError.NotFound("user not found"))

        val target = newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll().where { Users.id eq targetId }.firstOrNull()
        } ?: raise(AppError.NotFound("user not found"))

        if (target[Users.role] == UserRole.ADMIN) {
            val otherAdmins = newSuspendedTransaction(Dispatchers.IO) {
                Users.selectAll().where {
                    (Users.role eq UserRole.ADMIN) and (Users.id neq targetId)
                }.count()
            }
            if (otherAdmins == 0L) raise(AppError.Forbidden("you cannot delete the last admin account"))
        }

        purgeUser(targetId).bind()
        "user deleted"
    }

    override suspend fun rejectUser(
        targetId: String,
        admin: AuthenticatedUser
    ): Either<AppError, String> = either {
        admin.requireAdminAccess().bind()

        val target = newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll().where { Users.id eq targetId }.firstOrNull()
        } ?: raise(AppError.NotFound("user not found"))

        if (target[Users.approvalStatus] != ApprovalStatus.PENDING) {
            raise(AppError.BadRequest("only pending registrations can be rejected"))
        }

        purgeUser(targetId).bind()
        "registration rejected"
    }

    /**
     * Deletes a user and every record they own.
     *
     * Child rows are removed before their parents regardless of the rule the live constraint
     * carries — everything in [USER_OWNED_CHILD_COLUMNS], plus `todo_instances` and the
     * `approvedById` self-reference. A RESTRICT reference left behind rolls the whole purge back
     * and returns a 500, and which references are RESTRICT is not something this should have to
     * know (see "Foreign Keys: Flyway Writes Them, Exposed Owns Them" in docs/DATA_MODEL.md).
     *
     * A reference this does not know about still rolls the transaction back. It now comes out as
     * a 409 naming the constraint in the server log rather than as the status-page fallback's
     * "An unexpected error occurred", which said nothing about what to clear.
     */
    private suspend fun purgeUser(targetId: String): Either<AppError, Unit> = try {
        purgeUserInTransaction(targetId)
        Unit.right()
    } catch (error: ExposedSQLException) {
        // Only the constraint name is logged. The driver puts the offending key in the message,
        // and that is user data under the telemetry rules in AGENTS.md.
        val constraint = (error.cause as? PSQLException)?.serverErrorMessage?.constraint
        logger.error("Purging a user was rolled back by constraint {}", constraint ?: "<unknown>")
        AppError.Conflict(
            "this account still has data referencing it and was not deleted; " +
                "the server log names the constraint that blocked it",
        ).left()
    }

    private suspend fun purgeUserInTransaction(targetId: String) {
        newSuspendedTransaction(Dispatchers.IO) {
            // Share rows have no DB-level cascade (see ListShares), so clean up
            // memberships the user holds AND memberships on lists they own —
            // before the list rows themselves go away.
            val ownedListIds = Lists
                .select(Lists.id)
                .where { Lists.userID eq targetId }
                .map { it[Lists.id] }
            if (ownedListIds.isNotEmpty()) {
                ListShares.deleteWhere { ListShares.listID inList ownedListIds }
            }
            ListShares.deleteWhere { ListShares.userID eq targetId }
            val ownedFloaterListIds = FloaterLists
                .select(FloaterLists.id)
                .where { FloaterLists.userID eq targetId }
                .map { it[FloaterLists.id] }
            if (ownedFloaterListIds.isNotEmpty()) {
                FloaterListShares.deleteWhere { FloaterListShares.listID inList ownedFloaterListIds }
            }
            FloaterListShares.deleteWhere { FloaterListShares.userID eq targetId }

            // Per-occurrence overrides go before the todos that own them. V26 makes
            // todo_instances -> todos ON DELETE CASCADE, so this is now belt-and-braces rather
            // than load-bearing, but it is the ordering that keeps working if the rule ever
            // drifts back. Same child-first order as ListService.deleteLists.
            val ownedTodoIds = Todos
                .select(Todos.id)
                .where { Todos.userID eq targetId }
                .map { it[Todos.id] }
            if (ownedTodoIds.isNotEmpty()) {
                TodoInstances.deleteWhere { TodoInstances.todoId inList ownedTodoIds }
            }

            USER_OWNED_CHILD_COLUMNS.forEach { column ->
                column.table.deleteWhere { column eq targetId }
            }

            // "User".approvedById is a RESTRICT self-reference, so an admin who approved anyone
            // cannot be deleted while those rows still point at them. The column is nullable.
            Users.update({ Users.approvedById eq targetId }) {
                it[Users.approvedById] = null
            }

            Users.deleteWhere { Users.id eq targetId }
        }

        // AuthUserCache holds a 30s TTL, so without this a deleted user's session keeps
        // authenticating against a row that no longer exists. Runs after the commit because
        // revocation also touches the API-key service and the cache.
        sessionControl.revokeUserSessions(targetId, revokeApiKeys = true)
    }

    override suspend fun resetPassword(
        targetId: String,
        admin: AuthenticatedUser
    ): Either<AppError, String> = either {
        admin.requireAdminAccess().bind()

        val target = newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll().where { Users.id eq targetId }.firstOrNull()
        } ?: raise(AppError.NotFound("user not found"))

        // Admin accounts manage their own password under Settings; they can't be
        // reset from the admin panel.
        if (target[Users.role] == UserRole.ADMIN) {
            raise(AppError.Forbidden("admin account passwords can't be reset here"))
        }

        val generatedPassword = generatePassword()
        val newHash = passwordService.hashPassword(generatedPassword)

        newSuspendedTransaction(Dispatchers.IO) {
            Users.update({ Users.id eq target[Users.id] }) {
                it[Users.password] = newHash
                it[Users.requirePasswordChange] = true
                // An admin reset clears any self-service lockout and the pending request,
                // so the user is no longer flagged on the admin panel afterwards.
                it[Users.securityQuestionFailCount] = 0
                it[Users.pendingAdminReset] = false
                it[Users.adminResetRequestedAt] = null
                it[Users.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
        }

        // Force every existing session for this user to re-authenticate with the
        // new temporary password, and revoke the full-account API key so it can't
        // outlive the reset.
        sessionControl.revokeUserSessions(targetId, revokeApiKeys = true)

        generatedPassword
    }

    /**
     * Dismisses a pending reset request and clears the self-service lockout, without touching the
     * password or any session.
     *
     * Unlike [resetPassword] this is allowed against ADMIN targets, including the caller. Anyone
     * can POST /api/auth/request-admin-reset for any username, so without this an admin who was
     * flagged — or who simply mistyped their own security answers four times — had no in-app way
     * back: [resetPassword] refuses admin targets by design. Clearing a notification flag and a
     * failure counter grants no capability an admin does not already have.
     */
    override suspend fun clearResetRequest(
        targetId: String,
        admin: AuthenticatedUser,
    ): Either<AppError, String> = either {
        admin.requireAdminAccess().bind()

        val updated = newSuspendedTransaction(Dispatchers.IO) {
            Users.update({ Users.id eq targetId }) {
                it[Users.securityQuestionFailCount] = 0
                it[Users.pendingAdminReset] = false
                it[Users.adminResetRequestedAt] = null
                it[Users.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
        }
        if (updated == 0) raise(AppError.NotFound("user not found"))

        "reset request cleared"
    }

    /**
     * Builds a random temporary password that always satisfies the registration
     * policy (>= 8 chars, at least one uppercase letter and one special char).
     */
    private fun generatePassword(): String {
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val lower = "abcdefghijkmnpqrstuvwxyz"
        val digits = "23456789"
        val special = "!@#\$%^&*-_=+?"
        val all = upper + lower + digits + special

        val required = listOf(
            upper.random(secureRandom),
            lower.random(secureRandom),
            digits.random(secureRandom),
            special.random(secureRandom),
        )
        val filler = (1..12).map { all[secureRandom.nextInt(all.length)] }
        return (required + filler).shuffled(secureRandom.asKotlinRandom()).joinToString("")
    }

    private fun String.random(random: SecureRandom): Char = this[random.nextInt(length)]

    companion object {
        private val secureRandom = SecureRandom()
        private val logger = LoggerFactory.getLogger(AdminServiceImpl::class.java)
    }
}
