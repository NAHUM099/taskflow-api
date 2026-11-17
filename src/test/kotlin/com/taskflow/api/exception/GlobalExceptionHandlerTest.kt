package com.taskflow.api.exception

import com.taskflow.api.exception.classException.TooManyLoginAttemptsException
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.InternalAuthenticationServiceException
import org.springframework.security.authentication.LockedException
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver
import java.sql.SQLException

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

    // --- Integridad de datos --------------------------------------------------

    @Test
    fun `email duplicado por restriccion UNIQUE devuelve 409 sin detalles de SQL`() {
        val request = MockHttpServletRequest("POST", "/users")
        val ex = integrityViolation(
            sqlState = "23505",
            detail = """ERROR: duplicate key value violates unique constraint "uk6dotkott2kjsp8vw4d0m25fb7" """
        )

        val response = handler.dataIntegrityViolation(ex, request)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("email ya existente", response.body?.message)
        assertEquals("/users", response.body?.path)

        val body = response.body.toString()
        assertFalse(body.contains("uk6dotkott2kjsp8vw4d0m25fb7"), "no debe exponer la constraint")
        assertFalse(body.contains("duplicate key"), "no debe exponer el mensaje de PostgreSQL")
    }

    @Test
    fun `violacion de clave foranea de tareas devuelve 409 generico`() {
        val request = MockHttpServletRequest("DELETE", "/users/7")
        val ex = integrityViolation(
            sqlState = "23503",
            detail = """ERROR: insert or update on table "tasks" violates foreign key """ +
                """constraint "fk6s1ob9k4ihi75xbxe2w0ylsdh""""
        )

        val response = handler.dataIntegrityViolation(ex, request)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(
            "El usuario tiene tareas asociadas y no se puede eliminar",
            response.body?.message
        )
        assertFalse(response.body.toString().contains("fk6s1ob9k4ihi75xbxe2w0ylsdh"))
    }

    @Test
    fun `violacion NOT NULL es un error de datos 400 y no un conflicto`() {
        val request = MockHttpServletRequest("POST", "/tasks")
        val ex = integrityViolation(sqlState = "23502", detail = "ERROR: null value in column \"title\"")

        val response = handler.dataIntegrityViolation(ex, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertFalse(response.body.toString().contains("title"), "no debe exponer la columna")
    }

    @Test
    fun `integrity violation sin SQLState conocido devuelve un 409 generico`() {
        val request = MockHttpServletRequest("POST", "/tasks")
        val ex = DataIntegrityViolationException("could not execute statement")

        val response = handler.dataIntegrityViolation(ex, request)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(
            "La operacion no se pudo completar por un conflicto de integridad de datos",
            response.body?.message
        )
    }

    // --- Validación de parámetros --------------------------------------------

    @Test
    fun `ConstraintViolationException devuelve 400 con el nombre del campo`() {
        val request = MockHttpServletRequest("GET", "/users")

        val path = mock(Path::class.java)
        val node = mock(Path.Node::class.java)
        `when`(node.name).thenReturn("name")
        `when`(path.iterator()).thenReturn(mutableListOf(node).iterator())

        val violation = mock(ConstraintViolation::class.java)
        `when`(violation.propertyPath).thenReturn(path)
        `when`(violation.message).thenReturn("nombre requerido")

        val ex = ConstraintViolationException(setOf<ConstraintViolation<*>>(violation))

        val response = handler.constraintViolation(ex, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(mapOf("name" to "nombre requerido"), response.body?.details)
        assertFalse(response.body.toString().contains("com.taskflow"), "no debe exponer paquetes")
    }

    // --- Seguridad fuera de la cadena de filtros ------------------------------

    @Test
    fun `AccessDeniedException en el controlador responde 403 y no 500`() {
        val request = MockHttpServletRequest("DELETE", "/users/7")

        val response = handler.accessDenied(AccessDeniedException("denegado"), request)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("No tienes permisos para realizar esta acción", response.body?.message)
        assertFalse(response.body.toString().contains("denegado"))
    }

    @Test
    fun `AuthenticationException sin manejador propio responde 401 generico`() {
        val request = MockHttpServletRequest("POST", "/auth/login")

        val response = handler.authentication(LockedException("cuenta bloqueada"), request)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("usuario o contraseña incorrecto", response.body?.message)
        assertFalse(response.body.toString().contains("bloqueada"))
    }

    // --- Último recurso -------------------------------------------------------

    @Test
    fun `una excepcion inesperada devuelve 500 sin mensaje interno`() {
        val request = MockHttpServletRequest("GET", "/tasks")
        val ex = IllegalStateException("fallo con SELECT * FROM users WHERE password='x'")

        val response = handler.unexpectedError(ex, request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Ocurrió un error en el servidor", response.body?.message)
        assertEquals("/tasks", response.body?.path)

        val body = response.body.toString()
        assertFalse(body.contains("SELECT"), "no debe exponer SQL")
        assertFalse(body.contains("IllegalStateException"), "no debe exponer clases internas")
    }

    @Test
    fun `el manejador general no se come las excepciones especificas`() {
        val resolver = ExceptionHandlerMethodResolver(GlobalExceptionHandler::class.java)

        assertEquals("unexpectedError", resolver.resolveMethod(RuntimeException("boom"))?.name)
        assertEquals(
            "dataIntegrityViolation",
            resolver.resolveMethod(DataIntegrityViolationException("boom"))?.name
        )
        assertEquals("disabledException", resolver.resolveMethod(DisabledException("off"))?.name)
        assertEquals("accessDenied", resolver.resolveMethod(AccessDeniedException("denegado"))?.name)
        assertEquals("authentication", resolver.resolveMethod(LockedException("locked"))?.name)
        assertEquals(
            "authenticationServiceFailure",
            resolver.resolveMethod(InternalAuthenticationServiceException("db caida"))?.name
        )
    }

    private fun integrityViolation(sqlState: String, detail: String): DataIntegrityViolationException =
        DataIntegrityViolationException("could not execute statement", SQLException(detail, sqlState))
}
