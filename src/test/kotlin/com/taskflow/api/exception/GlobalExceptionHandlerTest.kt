package com.taskflow.api.exception

import com.taskflow.api.exception.classException.TooManyLoginAttemptsException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `DisabledException devuelve 401 sin exponer el estado de la cuenta`() {
        val request = MockHttpServletRequest("POST", "/auth/login")

        val response = handler.disabledException(request)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("usuario o contraseña incorrecto", response.body?.message)
        assertEquals("/auth/login", response.body?.path)
    }

    @Test
    fun `BadCredentialsException devuelve 401`() {
        val request = MockHttpServletRequest("POST", "/auth/login")
        val ex = BadCredentialsException("credenciales incorrectas")

        val response = handler.badCredentialsException(ex, request)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.body?.status)
    }

    @Test
    fun `DisabledException esta mapeada en el ControllerAdvice`() {
        val resolver = ExceptionHandlerMethodResolver(GlobalExceptionHandler::class.java)

        val method = requireNotNull(resolver.resolveMethod(DisabledException("User is disabled")))

        assertEquals("disabledException", method.name)
    }

    @Test
    fun `TooManyLoginAttemptsException devuelve 429 con Retry-After y sin datos de la cuenta`() {
        val request = MockHttpServletRequest("POST", "/auth/login")

        val response = handler.tooManyLoginAttempts(TooManyLoginAttemptsException(30), request)

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.statusCode)
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.body?.status)
        assertEquals("30", response.headers.getFirst(HttpHeaders.RETRY_AFTER))
        assertEquals("/auth/login", response.body?.path)

        val message = response.body?.message.orEmpty()
        assertTrue(message.contains("Demasiados intentos"))
        assertFalse(message.contains("@"), "el mensaje no debe exponer el correo")
    }
}
