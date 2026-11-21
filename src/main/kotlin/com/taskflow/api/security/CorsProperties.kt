package com.taskflow.api.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

/**
 * Orígenes autorizados para peticiones CORS.
 *
 * Se enlaza desde `taskflow.cors.allowed-origins`, que a su vez toma la
 * variable de entorno `CORS_ALLOWED_ORIGINS` (lista separada por comas). No se
 * admiten comodines: el backend debe conocer explícitamente el origen del
 * frontend, también en producción.
 */
@ConfigurationProperties(prefix = CorsProperties.PREFIX)
data class CorsProperties(
    val allowedOrigins: List<String> = listOf(DEFAULT_ORIGIN)
) {
    init {
        require(allowedOrigins.isNotEmpty()) {
            "taskflow.cors.allowed-origins debe contener al menos un origen"
        }
        require(allowedOrigins.none { it.isBlank() }) {
            "taskflow.cors.allowed-origins no puede contener valores vacios"
        }
        require(allowedOrigins.none { it.trim() == "*" }) {
            "taskflow.cors.allowed-origins no admite el comodin '*'"
        }
        require(allowedOrigins.all(::isHttpOrigin)) {
            "taskflow.cors.allowed-origins debe contener URLs http(s) sin ruta"
        }
    }

    private fun isHttpOrigin(value: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        return uri.scheme in setOf("http", "https") &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.query == null &&
            uri.fragment == null &&
            (uri.path.isNullOrEmpty() || uri.path == "/")
    }

    companion object {
        const val PREFIX = "taskflow.cors"
        const val DEFAULT_ORIGIN = "http://localhost:3000"
    }
}
