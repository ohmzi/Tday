package com.ohmz.tday.security

import com.ohmz.tday.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import io.ktor.server.request.ApplicationRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class CaptchaResult(val ok: Boolean, val reason: String? = null)

interface CaptchaService {
    fun isConfigured(): Boolean
    suspend fun verify(token: String?, request: ApplicationRequest, action: String): CaptchaResult
    fun extractTokenFromJson(body: kotlinx.serialization.json.JsonObject?): String?
}

class CaptchaServiceImpl(
    private val config: AppConfig,
    private val clientSignals: ClientSignals,
) : CaptchaService {
    private val turnstileUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify"
    private val client = HttpClient(CIO)

    override fun isConfigured(): Boolean = !config.captchaSecret.isNullOrBlank()

    override suspend fun verify(
        token: String?,
        request: ApplicationRequest,
        @Suppress("UNUSED_PARAMETER") action: String,
    ): CaptchaResult {
        if (!isConfigured()) return CaptchaResult(ok = true)
        val trimmed = token?.trim()
        if (trimmed.isNullOrEmpty()) return CaptchaResult(ok = false, reason = "missing_captcha_token")
        val secret = config.captchaSecret ?: return CaptchaResult(ok = false, reason = "captcha_secret_missing")

        return try {
            val response = client.submitForm(
                url = turnstileUrl,
                formParameters = parameters {
                    append("secret", secret)
                    append("response", trimmed)
                    append("remoteip", clientSignals.getClientIp(request))
                },
            )

            if (response.status.value !in 200..299) {
                return CaptchaResult(ok = false, reason = "captcha_http_${response.status.value}")
            }

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val success = body["success"]?.jsonPrimitive?.booleanOrNull
            if (success == true) {
                CaptchaResult(ok = true)
            } else {
                val errorCode = body["error-codes"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
                    ?: "captcha_invalid"
                CaptchaResult(ok = false, reason = errorCode)
            }
        } catch (e: Exception) {
            CaptchaResult(ok = false, reason = e.message ?: "captcha_verification_error")
        }
    }

    override fun extractTokenFromJson(body: kotlinx.serialization.json.JsonObject?): String? {
        if (body == null) return null
        body["captchaToken"]?.jsonPrimitive?.contentOrNull?.trim()?.ifEmpty { null }?.let { return it }
        body["cf-turnstile-response"]?.jsonPrimitive?.contentOrNull?.trim()?.ifEmpty { null }?.let { return it }
        return null
    }
}
