package com.ohmz.tday.models.response

import com.ohmz.tday.security.SecurityQuestion
import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val maxStorage: Double,
    val usedStoraged: Double,
    val enableEncryption: Boolean,
    val protectedSymmetricKey: String?,
)

@Serializable
data class UserProfileResponse(
    val id: String,
    val name: String?,
    val username: String,
    val image: String?,
    val role: String,
    val approvalStatus: String,
    val createdAt: String,
)

@Serializable
data class AdminUserResponse(
    val id: String,
    val name: String?,
    val username: String,
    val role: String,
    val approvalStatus: String,
    val createdAt: String,
    val approvedAt: String?,
    val pendingAdminReset: Boolean = false,
    val adminResetRequestedAt: String? = null,
)

@Serializable
data class SecurityQuestionsResponse(
    val questions: List<SecurityQuestion>,
)

@Serializable
data class SecurityQuestionStatusResponse(
    val questionIds: List<Int>,
    val requireSecurityQuestions: Boolean,
)

@Serializable
data class SecurityAnswerResult(
    val questionId: Int,
    val correct: Boolean,
)

@Serializable
data class VerifySecurityAnswersResponse(
    val valid: Boolean,
    val results: List<SecurityAnswerResult>,
)
