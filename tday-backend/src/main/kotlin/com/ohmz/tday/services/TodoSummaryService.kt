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

object TodoSummaryService {
    private val logger = LoggerFactory.getLogger(TodoSummaryService::class.java)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = AppConfig.ollamaTimeoutMs
        }
    }

    suspend fun generateSummary(prompt: String): String? {
        return try {
            val bodyJson = json.encodeToString(
                OllamaRequest.serializer(),
                OllamaRequest(
                    model = AppConfig.ollamaModel,
                    prompt = prompt,
                    stream = false,
                ),
            )
            val response = client.post("${AppConfig.ollamaUrl}/api/generate") {
                contentType(ContentType.Application.Json)
                setBody(bodyJson)
            }

            if (!response.status.isSuccess()) {
                logger.warn("Ollama returned ${response.status}")
                return null
            }

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["response"]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            logger.warn("Ollama request failed: ${e.message}")
            null
        }
    }

    suspend fun isHealthy(): Boolean {
        return try {
            val response = client.get("${AppConfig.ollamaUrl}/api/tags")
            response.status.isSuccess()
        } catch (_: Exception) {
            false
        }
    }

    @Serializable
    private data class OllamaRequest(
        val model: String,
        val prompt: String,
        val stream: Boolean = false,
    )
}
