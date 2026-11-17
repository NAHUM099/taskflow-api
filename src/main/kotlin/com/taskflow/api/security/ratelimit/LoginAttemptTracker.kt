package com.taskflow.api.security.ratelimit

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Contador de intentos fallidos de login con ventana fija y caducidad.
 *
 * Mantiene dos índices independientes:
 * - `(ip, email)`: frena los intentos repetidos contra una cuenta concreta
 *   desde un mismo origen.
 * - `ip`: frena el barrido de muchas cuentas distintas desde un mismo origen.
 *
 * Nunca indexa solo por email: así un atacante no puede bloquear la cuenta de
 * otra persona enviando intentos desde su propia IP. Cuando la ventana caduca,
 * el contador se reinicia solo, por lo que el bloqueo nunca es permanente.
 *
 * Es una clase pura (sin Spring) para poder probar el comportamiento temporal
 * inyectando un reloj determinista mediante [now]. Es la implementación
 * [LoginAttemptStore] que se usa por defecto.
 */
class LoginAttemptTracker(
    private val windowMillis: Long,
    private val maxAttemptsPerAccount: Int,
    private val maxAttemptsPerIp: Int,
    private val now: () -> Long = System::currentTimeMillis
) : LoginAttemptStore {

    private class Window(var startMillis: Long, var attempts: Int)

    private val attemptsByAccount = ConcurrentHashMap<String, Window>()
    private val attemptsByIp = ConcurrentHashMap<String, Window>()

    override fun secondsUntilAllowed(clientIp: String, email: String): Long {
        val currentTime = now()
        val accountWait = remainingMillis(
            attemptsByAccount[accountKey(clientIp, email)],
            maxAttemptsPerAccount,
            currentTime
        )
        val ipWait = remainingMillis(attemptsByIp[clientIp], maxAttemptsPerIp, currentTime)
        return ceilToSeconds(max(accountWait, ipWait))
    }

    override fun recordFailure(clientIp: String, email: String) {
        val currentTime = now()
        increment(attemptsByAccount, accountKey(clientIp, email), currentTime)
        increment(attemptsByIp, clientIp, currentTime)
        pruneExpired(currentTime)
    }

    override fun recordSuccess(clientIp: String, email: String) {
        attemptsByAccount.remove(accountKey(clientIp, email))
    }

    private fun remainingMillis(window: Window?, limit: Int, currentTime: Long): Long {
        if (window == null || window.attempts < limit) {
            return 0
        }
        val elapsed = currentTime - window.startMillis
        return if (elapsed >= windowMillis) 0 else windowMillis - elapsed
    }

    private fun increment(
        windows: ConcurrentHashMap<String, Window>,
        key: String,
        currentTime: Long
    ) {
        windows.compute(key) { _, current ->
            if (current == null || currentTime - current.startMillis >= windowMillis) {
                Window(currentTime, 1)
            } else {
                current.attempts += 1
                current
            }
        }
    }

    private fun pruneExpired(currentTime: Long) {
        if (attemptsByAccount.size + attemptsByIp.size <= MAX_TRACKED_KEYS) {
            return
        }
        removeExpired(attemptsByAccount, currentTime)
        removeExpired(attemptsByIp, currentTime)
    }

    private fun removeExpired(
        windows: ConcurrentHashMap<String, Window>,
        currentTime: Long
    ) {
        windows.forEach { (key, window) ->
            if (currentTime - window.startMillis >= windowMillis) {
                windows.remove(key, window)
            }
        }
    }

    private fun accountKey(clientIp: String, email: String): String =
        "$clientIp|${email.trim().lowercase()}"

    private fun ceilToSeconds(millis: Long): Long =
        if (millis <= 0) 0 else (millis + 999) / 1000

    private companion object {
        /** Cota de memoria: al superarla se limpian las ventanas ya caducadas. */
        const val MAX_TRACKED_KEYS = 10_000
    }
}
