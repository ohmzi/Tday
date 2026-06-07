package com.ohmz.tday.services

import com.ohmz.tday.db.tables.UserSecurityQuestions
import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.models.request.SecurityAnswerInput
import com.ohmz.tday.security.AuthUserCache
import com.ohmz.tday.security.ClientSignals
import com.ohmz.tday.security.PasswordService
import com.ohmz.tday.security.SecurityQuestion
import com.ohmz.tday.security.SecurityQuestions
import com.ohmz.tday.security.SessionControl
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Lockout triggers once consecutive failures EXCEED this count (i.e. on the 4th miss). */
const val SECURITY_QUESTION_FAIL_LIMIT = 3

enum class ResetOutcome { SUCCESS, FAILED, LOCKED }

data class SecurityQuestionStatus(
    val questionIds: List<Int>,
    val requireSecurityQuestions: Boolean,
)

interface SecurityQuestionService {
    /** Always returns exactly two questions: the user's real pair, or a stable decoy pair. */
    suspend fun questionsForUsername(rawUsername: String): List<SecurityQuestion>

    /** Verifies both answers and, on success, resets the password and revokes sessions. */
    suspend fun verifyAndReset(rawUsername: String, answers: List<SecurityAnswerInput>, newPassword: String): ResetOutcome

    /** Flags the account (if it exists) as requesting an admin password reset. Idempotent. */
    suspend fun requestAdminReset(rawUsername: String)

    /** The signed-in user's chosen question ids and whether they still need to set them. */
    suspend fun statusFor(userId: String): SecurityQuestionStatus

    /** Replaces the signed-in user's questions with the given 2-of-3 selection. */
    suspend fun setQuestions(userId: String, answers: List<SecurityAnswerInput>)
}

class SecurityQuestionServiceImpl(
    private val passwordService: PasswordService,
    private val sessionControl: SessionControl,
    private val clientSignals: ClientSignals,
    private val authUserCache: AuthUserCache,
) : SecurityQuestionService {

    // A throwaway hash so a missing/unconfigured account still pays the PBKDF2 cost,
    // keeping response timing indistinguishable from a real verification.
    private val dummyHash: String by lazy { passwordService.hashPassword("dummy-answer-for-timing") }

    override suspend fun questionsForUsername(rawUsername: String): List<SecurityQuestion> {
        val username = normalize(rawUsername)
        val hashHex = clientSignals.hashSecurityValue("secq:$username")
        val ids = newSuspendedTransaction(Dispatchers.IO) {
            val userId = findUserId(username)
            if (userId != null) {
                val rows = UserSecurityQuestions.selectAll()
                    .where { UserSecurityQuestions.userID eq userId }
                    .map { it[UserSecurityQuestions.questionId] }
                // Ask a stable 2 of the user's stored questions (handles legacy
                // 2-question accounts and current 3-question accounts alike).
                if (rows.size >= 2) SecurityQuestions.stableSubset(rows, hashHex, 2) else null
            } else {
                null
            }
        } ?: SecurityQuestions.decoyPair(hashHex)
        return SecurityQuestions.questionsFor(ids)
    }

    override suspend fun verifyAndReset(
        rawUsername: String,
        answers: List<SecurityAnswerInput>,
        newPassword: String,
    ): ResetOutcome {
        val username = normalize(rawUsername)
        // The transaction returns the outcome plus, on success, the id whose sessions
        // must be revoked (revocation touches the API-key service + cache, so it runs
        // after the row is committed rather than inside the transaction body).
        val (outcome, resetUserId) = newSuspendedTransaction(Dispatchers.IO) {
            val userRow = Users.selectAll().where { Users.username.lowerCase() eq username }.firstOrNull()

            // Unknown or not-yet-configured account: equalise timing, then fail generically.
            if (userRow == null || userRow[Users.requireSecurityQuestions]) {
                passwordService.verifyPassword("x", dummyHash)
                return@newSuspendedTransaction ResetOutcome.FAILED to null
            }

            val userId = userRow[Users.id]
            val locked = userRow[Users.securityQuestionFailCount] > SECURITY_QUESTION_FAIL_LIMIT ||
                userRow[Users.pendingAdminReset]
            if (locked) {
                passwordService.verifyPassword("x", dummyHash)
                return@newSuspendedTransaction ResetOutcome.LOCKED to null
            }

            val storedHashes = UserSecurityQuestions.selectAll()
                .where { UserSecurityQuestions.userID eq userId }
                .associate { it[UserSecurityQuestions.questionId] to it[UserSecurityQuestions.answerHash] }

            // Evaluate every answer with no short-circuit so timing/response never reveal
            // which one was wrong; require both supplied answers to match their question.
            var matched = 0
            for (answer in answers) {
                val hash = storedHashes[answer.questionId]
                val verification = if (hash != null) {
                    passwordService.verifyPassword(SecurityQuestions.normalizeAnswer(answer.answer), hash)
                } else {
                    passwordService.verifyPassword("x", dummyHash)
                    null
                }
                if (verification?.valid == true) matched++
            }
            val distinctIds = answers.map { it.questionId }.toSet()
            val success = matched == 2 && distinctIds.size == 2 && storedHashes.keys.containsAll(distinctIds)

            val now = LocalDateTime.now(ZoneOffset.UTC)
            if (success) {
                Users.update({ Users.id eq userId }) {
                    it[Users.password] = passwordService.hashPassword(newPassword)
                    it[Users.requirePasswordChange] = false
                    it[Users.securityQuestionFailCount] = 0
                    it[Users.pendingAdminReset] = false
                    it[Users.adminResetRequestedAt] = null
                    it[Users.updatedAt] = now
                }
                ResetOutcome.SUCCESS to userId
            } else {
                Users.update({ Users.id eq userId }) {
                    with(SqlExpressionBuilder) {
                        it[Users.securityQuestionFailCount] = Users.securityQuestionFailCount + 1
                    }
                    it[Users.updatedAt] = now
                }
                ResetOutcome.FAILED to null
            }
        }
        if (resetUserId != null) sessionControl.revokeUserSessions(resetUserId, revokeApiKeys = true)
        return outcome
    }

    override suspend fun requestAdminReset(rawUsername: String) {
        val username = normalize(rawUsername)
        val now = LocalDateTime.now(ZoneOffset.UTC)
        newSuspendedTransaction(Dispatchers.IO) {
            Users.update({ Users.username.lowerCase() eq username }) {
                it[Users.pendingAdminReset] = true
                it[Users.adminResetRequestedAt] = now
                it[Users.updatedAt] = now
            }
        }
    }

    override suspend fun statusFor(userId: String): SecurityQuestionStatus =
        newSuspendedTransaction(Dispatchers.IO) {
            val requireSet = Users.selectAll().where { Users.id eq userId }.firstOrNull()
                ?.get(Users.requireSecurityQuestions) ?: true
            val ids = UserSecurityQuestions.selectAll()
                .where { UserSecurityQuestions.userID eq userId }
                .map { it[UserSecurityQuestions.questionId] }
                .sorted()
            SecurityQuestionStatus(questionIds = ids, requireSecurityQuestions = requireSet)
        }

    override suspend fun setQuestions(userId: String, answers: List<SecurityAnswerInput>) {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        newSuspendedTransaction(Dispatchers.IO) {
            UserSecurityQuestions.deleteWhere { UserSecurityQuestions.userID eq userId }
            for (answer in answers) {
                UserSecurityQuestions.insert {
                    it[id] = CuidGenerator.newCuid()
                    it[UserSecurityQuestions.userID] = userId
                    it[questionId] = answer.questionId
                    it[answerHash] = passwordService.hashPassword(SecurityQuestions.normalizeAnswer(answer.answer))
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
            Users.update({ Users.id eq userId }) {
                it[Users.requireSecurityQuestions] = false
                it[Users.updatedAt] = now
            }
        }
        authUserCache.invalidate(userId)
    }

    private fun normalize(raw: String): String = raw.trim().lowercase()

    private fun findUserId(normalizedUsername: String): String? =
        Users.selectAll().where { Users.username.lowerCase() eq normalizedUsername }
            .firstOrNull()?.get(Users.id)
}
