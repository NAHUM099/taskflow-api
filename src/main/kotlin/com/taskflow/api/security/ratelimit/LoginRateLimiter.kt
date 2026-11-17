package com.taskflow.api.security.ratelimit

import com.taskflow.api.exception.classException.TooManyLoginAttemptsException
import org.springframework.stereotype.Component

/**
 * Punto de entrada del rate limiting de login.
 *
 * Traduce [LoginRateLimitProperties] a las operaciones que necesita el flujo de
 * autenticación —comprobar, registrar fallo y registrar éxito— delegando el
 * almacenamiento en [LoginAttemptStore]: en memoria por defecto o en Redis para
 * compartir el estado entre réplicas.
 */
@Component
class LoginRateLimiter(
    private val properties: LoginRateLimitProperties,
    private val store: LoginAttemptStore
) {

    /**
     * Lanza [TooManyLoginAttemptsException] si el cliente ya agotó su cuota.
     * No consulta la base de datos ni revela si la cuenta existe.
     */
    fun checkAllowed(clientIp: String, email: String) {
        if (!properties.enabled) {
            return
        }

        val retryAfterSeconds = store.secondsUntilAllowed(clientIp, email)
        if (retryAfterSeconds > 0) {
            throw TooManyLoginAttemptsException(retryAfterSeconds)
        }
    }

    fun recordFailure(clientIp: String, email: String) {
        if (!properties.enabled) {
            return
        }
        store.recordFailure(clientIp, email)
    }

    fun recordSuccess(clientIp: String, email: String) {
        if (!properties.enabled) {
            return
        }
        store.recordSuccess(clientIp, email)
    }
}
