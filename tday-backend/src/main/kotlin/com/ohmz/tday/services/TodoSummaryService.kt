package com.ohmz.tday.services

import com.ohmz.tday.config.AppConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

interface TodoSummaryService {
    suspend fun generateSummary(prompt: String): String?
    suspend fun isHealthy(): Boolean
    suspend fun warmUp()
}

class TodoSummaryServiceImpl(private val config: AppConfig) : TodoSummaryService {
    private val logger = LoggerFactory.getLogger(TodoSummaryServiceImpl::class.java)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = config.ollamaTimeoutMs
        }
    }

    override suspend fun generateSummary(prompt: String): String? {
        if (config.ollamaUrl.isBlank()) return null

        return try {
            val bodyJson = json.encodeToString(
                OllamaChatRequest.serializer(),
                OllamaChatRequest(
                    model = config.ollamaModel,
                    messages = listOf(OllamaMessage(role = "user", content = prompt)),
                    stream = false,
                    think = false,
                    options = OllamaOptions(
                        numPredict = 120,
                        temperature = 0.2,
                    ),
                ),
            )
            val response = client.post("${config.ollamaUrl}/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(bodyJson)
            }

            if (!response.status.isSuccess()) {
                logger.warn("Ollama returned ${response.status}")
                return null
            }

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val content = body["message"]
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
                ?: body["response"]?.jsonPrimitive?.contentOrNull
            cleanModelResponse(content)
        } catch (e: Exception) {
            logger.warn("Ollama request failed: ${e.message}")
            null
        }
    }

    override suspend fun isHealthy(): Boolean {
        if (config.ollamaUrl.isBlank()) return false

        return try {
            val response = client.get("${config.ollamaUrl}/api/tags")
            response.status.isSuccess()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun warmUp() {
        if (config.ollamaUrl.isBlank()) return
        generateSummary("Reply with exactly: ready")
    }

    private fun cleanModelResponse(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val withoutThinkTags = trimmed.replace(Regex("(?is)<think>.*?</think>"), "").trim()
        val withoutThinkingPrelude = withoutThinkTags
            .substringAfter("...done thinking.", withoutThinkTags)
            .trim()
        val cleaned = if (withoutThinkingPrelude.startsWith("Thinking", ignoreCase = true)) {
            withoutThinkingPrelude.lineSequence().lastOrNull { it.isNotBlank() }?.trim().orEmpty()
        } else {
            withoutThinkingPrelude
        }
        return cleaned.takeIf { it.isNotBlank() }
    }

    @Serializable
    private data class OllamaChatRequest(
        val model: String,
        val messages: List<OllamaMessage>,
        val stream: Boolean = false,
        val think: Boolean = false,
        val options: OllamaOptions? = null,
    )

    @Serializable
    private data class OllamaMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class OllamaOptions(
        @kotlinx.serialization.SerialName("num_predict")
        val numPredict: Int,
        val temperature: Double,
    )
}
