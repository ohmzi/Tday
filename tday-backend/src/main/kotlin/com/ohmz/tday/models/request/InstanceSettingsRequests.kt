package com.ohmz.tday.models.request

import kotlinx.serialization.Serializable

@Serializable
data class ServerTelemetryPatchRequest(val enabled: Boolean)
