package com.ohmz.tday.compose.core.data

sealed class ServerProbeException(message: String) : Exception(message) {
    class InvalidUrl : ServerProbeException("Invalid server URL")

    class InsecureTransport :
        ServerProbeException("Use HTTPS for remote servers. HTTP is allowed only for local development.")

    class NotTdayServer :
        ServerProbeException("This server is reachable, but it is not a compatible T'Day authentication server.")

    class CertificateChanged(val trustedServerKey: String) :
        ServerProbeException(
            "Server certificate changed for $trustedServerKey. This may indicate a MITM or cert rotation. Reset trust to continue.",
        )
}
