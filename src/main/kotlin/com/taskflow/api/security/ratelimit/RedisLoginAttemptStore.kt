package com.taskflow.api.security.ratelimit

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import kotlin.math.max

/**
 * Implementación con estado compartido de [LoginAttemptStore].
 *
 * Usa dos claves de Redis con **ventana fija**: `account:<ip>|<email>` y
 * `ip:<ip>`. Cada clave se incrementa con `INCR` (atómico) y recibe un `EXPIRE`
 * al crearse, así que el bloqueo caduca solo y nunca es permanente. Al ser
 * estado externo, el límite se comparte entre todas las réplicas del backend.
 *
 * Si Redis no responde se aplica *fail-open*: se registra el fallo y se deja
 * pasar la petición. La alternativa (*fail-closed*) dejaría a todo el mundo sin
 * poder iniciar sesión por una caída del caché, un riesgo mayor para la
 * disponibilidad que la ventana de fuerza bruta que se abre.
 */
class RedisLoginAttemptStore(
    private val redis: StringRedisTemplate,
    private val properties: LoginRateLimitProperties,
    private val keyPrefix: String = DEFAULT_KEY_PREFIX
) : LoginAttemptStore {

    override fun secondsUntilAllowed(clientIp: String, email: String): Long {
        val accountWait = waitSeconds(accountKey(clientIp, email), properties.maxAttemptsPerAccount)
        val ipWait = waitSeconds(ipKey(clientIp), properties.maxAttemptsPerIp)
        return max(accountWait, ipWait)
    }

    override fun recordFailure(clientIp: String, email: String) {
        increment(accountKey(clientIp, email))
        increment(ipKey(clientIp))
    }

    override fun recordSuccess(clientIp: String, email: String) {
        runSafely(Unit, "liberar la cuota de la cuenta") {
            redis.delete(accountKey(clientIp, email))
        }
    }

    private fun waitSeconds(key: String, limit: Int): Long =
        runSafely(0L, "consultar los intentos de $key") {
            val attempts = redis.opsForValue().get(key)?.toIntOrNull()
            if (attempts == null || attempts < limit) {
                return@runSafely 0L
            }

            // -1 = clave sin caducidad, -2 = clave inexistente (carrera con el
            // EXPIRE). En ambos casos se asume la ventana completa.
            val ttl = redis.getExpire(key)
            if (ttl <= 0L) properties.window.seconds else ttl
        }

    private fun increment(key: String) {
        runSafely(Unit, "registrar un intento fallido en $key") {
            val attempts = redis.opsForValue().increment(key)
            if (attempts != null && attempts == 1L) {
                redis.expire(key, properties.window)
            }
        }
    }

    private fun <T> runSafely(fallback: T, action: String, block: () -> T): T =
        try {
            block()
        } catch (ex: DataAccessException) {
            logger.warn("Rate limiting del login sin Redis al {}: {}", action, ex.message)
            fallback
        }

    private fun accountKey(clientIp: String, email: String): String =
        "$keyPrefix:account:$clientIp|${email.trim().lowercase()}"

    private fun ipKey(clientIp: String): String = "$keyPrefix:ip:$clientIp"

    private companion object {
        const val DEFAULT_KEY_PREFIX = "taskflow:login-rate"

        val logger = LoggerFactory.getLogger(RedisLoginAttemptStore::class.java)
    }
}
