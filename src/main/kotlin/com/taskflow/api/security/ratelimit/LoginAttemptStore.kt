package com.taskflow.api.security.ratelimit

/**
 * Almacén de los intentos fallidos de login.
 *
 * Abstrae *dónde* se guardan los contadores para que el rate limiting pueda
 * centralizarse cuando hay más de una réplica:
 *
 * - [LoginAttemptTracker]: en memoria, válido para una sola instancia (por defecto).
 * - [RedisLoginAttemptStore]: estado compartido entre réplicas (requiere Redis).
 *
 * Las implementaciones reciben la IP y el email ya resueltos y son responsables
 * de normalizar el email para que el límite no se pueda eludir cambiando
 * mayúsculas.
 */
interface LoginAttemptStore {

    /**
     * Segundos que el cliente debe esperar antes de reintentar.
     * Devuelve `0` cuando el intento está permitido.
     */
    fun secondsUntilAllowed(clientIp: String, email: String): Long

    /** Registra un intento fallido en los contadores de `(IP, email)` y de `IP`. */
    fun recordFailure(clientIp: String, email: String)

    /** Un login correcto libera la cuota de esa cuenta para ese origen. */
    fun recordSuccess(clientIp: String, email: String)
}
