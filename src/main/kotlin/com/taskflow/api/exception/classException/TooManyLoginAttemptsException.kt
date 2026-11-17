package com.taskflow.api.exception.classException

/**
 * Se lanza cuando un cliente supera el número de intentos de login permitidos.
 *
 * [retryAfterSeconds] indica cuánto falta para que la ventana actual caduque y
 * se pueda volver a intentar; el manejador central lo publica en el header
 * `Retry-After`.
 */
class TooManyLoginAttemptsException(
    val retryAfterSeconds: Long
) : RuntimeException("Demasiados intentos de inicio de sesión")
