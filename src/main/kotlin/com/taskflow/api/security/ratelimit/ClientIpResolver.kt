package com.taskflow.api.security.ratelimit

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * Resuelve la IP del cliente usada como clave del rate limiting.
 *
 * Por defecto se usa `remoteAddr`, que no puede falsificar el cliente. Detrás
 * de un proxy de confianza se puede activar `trust-forwarded-headers` para leer
 * el primer valor de `X-Forwarded-For`; activarlo sin un proxy delante permite
 * eludir el límite falsificando el header.
 */
@Component
class ClientIpResolver(
    private val properties: LoginRateLimitProperties
) {

    fun resolve(request: HttpServletRequest): String {
        if (properties.trustForwardedHeaders) {
            val forwardedFor = request.getHeader(FORWARDED_FOR_HEADER)
            if (!forwardedFor.isNullOrBlank()) {
                return forwardedFor.split(',').first().trim()
            }
        }

        return request.remoteAddr ?: UNKNOWN_IP
    }

    private companion object {
        const val FORWARDED_FOR_HEADER = "X-Forwarded-For"
        const val UNKNOWN_IP = "unknown"
    }
}
