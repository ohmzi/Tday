package com.ohmz.tday.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class AppVersionDefaults(
    val version: String = "0.0.0",
    val compatibilityMode: String = "exact",
    val updateRequired: Boolean = false,
)

object AppVersionDefaultsLoader {
    private const val RESOURCE_NAME = "tday-version.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AppVersionDefaults {
        val raw = readResourceManifest() ?: readLocalManifest() ?: return AppVersionDefaults()
        return runCatching { parse(raw) }.getOrDefault(AppVersionDefaults())
    }

    private fun parse(raw: String): AppVersionDefaults {
        val root = json.parseToJsonElement(raw).jsonObject
        val compatibility = root["compatibility"] as? JsonObject
        return AppVersionDefaults(
            version = root["version"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "0.0.0" },
            compatibilityMode = compatibility
                ?.get("mode")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.ifBlank { null }
                ?: "exact",
            updateRequired = compatibility
                ?.get("updateRequired")
                ?.jsonPrimitive
                ?.booleanOrNull
                ?: false,
        )
    }

    private fun readResourceManifest(): String? {
        return Thread.currentThread().contextClassLoader
            ?.getResourceAsStream(RESOURCE_NAME)
            ?.bufferedReader()
            ?.use { it.readText() }
    }

    private fun readLocalManifest(): String? {
        return listOf(
            File("version.json"),
            File("../version.json"),
        ).firstOrNull { it.isFile }?.readText()
    }
}
