package com.taskflow.api.security.ratelimit

import com.taskflow.api.exception.classException.TooManyLoginAttemptsException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class LoginRateLimiterTest {

    @Test
    fun `al agotar la cuota lanza la excepcion con los segundos restantes`() {
        val limiter = limiter(
            LoginRateLimitProperties(
                window = Duration.ofSeconds(60),
                maxAttemptsPerAccount = 2,
                maxAttemptsPerIp = 10
            )
        )

        limiter.checkAllowed(IP, EMAIL)
        limiter.recordFailure(IP, EMAIL)
        limiter.recordFailure(IP, EMAIL)

        val ex = assertThrows(TooManyLoginAttemptsException::class.java) {
            limiter.checkAllowed(IP, EMAIL)
        }

        assertTrue(ex.retryAfterSeconds in 1..60)
    }

    @Test
    fun `deshabilitado deja pasar siempre y no acumula intentos`() {
        val limiter = limiter(
            LoginRateLimitProperties(
                enabled = false,
                maxAttemptsPerAccount = 1,
                maxAttemptsPerIp = 1
            )
        )

        repeat(5) {
            assertDoesNotThrow { limiter.checkAllowed(IP, EMAIL) }
            limiter.recordFailure(IP, EMAIL)
        }
    }

    /** Usa el almacén en memoria real, que es el que se configura por defecto. */
    private fun limiter(properties: LoginRateLimitProperties) = LoginRateLimiter(
        properties,
        LoginAttemptTracker(
            windowMillis = properties.window.toMillis(),
            maxAttemptsPerAccount = properties.maxAttemptsPerAccount,
            maxAttemptsPerIp = properties.maxAttemptsPerIp
        )
    )

    private companion object {
        const val IP = "192.168.1.10"
        const val EMAIL = "ana@taskflow.dev"
    }
}
