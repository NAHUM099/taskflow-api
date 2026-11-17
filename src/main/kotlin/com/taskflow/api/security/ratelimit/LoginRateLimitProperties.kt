package com.taskflow.api.security.ratelimit

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuración externa del rate limiting aplicado a `/auth/login`.
 *
 * Se enlaza desde `taskflow.security.login-rate-limit` en `application.yaml`,
 * por lo que puede ajustarse por entorno con variables de entorno sin tocar
 * código.
 */
@ConfigurationProperties(prefix = LoginRateLimitProperties.PREFIX)
data class LoginRateLimitProperties(
    val enabled: Boolean = true,
    val window: Duration = Duration.ofMinutes(5),
    val maxAttemptsPerAccount: Int = 5,
    val maxAttemptsPerIp: Int = 30,
    val trustForwardedHeaders: Boolean = false,
    /**
     * Dónde se guardan los contadores: `memory` (una instancia) o `redis`
     * (estado compartido entre réplicas).
     */
    val store: LoginAttemptStoreType = LoginAttemptStoreType.MEMORY
) {
    init {
        require(!window.isNegative && !window.isZero) {
            "taskflow.security.login-rate-limit.window debe ser mayor que cero"
        }
        require(maxAttemptsPerAccount > 0) {
            "taskflow.security.login-rate-limit.max-attempts-per-account debe ser mayor que cero"
        }
        require(maxAttemptsPerIp > 0) {
            "taskflow.security.login-rate-limit.max-attempts-per-ip debe ser mayor que cero"
        }
    }

    companion object {
        const val PREFIX = "taskflow.security.login-rate-limit"
    }
}

/** Backend de almacenamiento de los contadores de intentos fallidos. */
enum class LoginAttemptStoreType {
    /** Estado en memoria del proceso. Con varias réplicas el límite no es global. */
    MEMORY,

    /** Estado en Redis, compartido por todas las réplicas del backend. */
    REDIS
}
