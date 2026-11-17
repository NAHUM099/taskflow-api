package com.taskflow.api.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException

class SecurityErrorHandlerTest {

    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private lateinit var handler: SecurityErrorHandler

    @BeforeEach
    fun setUp() {
        handler = SecurityErrorHandler(objectMapper)
    }

    @Test
    fun `entry point responde 401 con formato ErrorResponse`() {
        val request = MockHttpServletRequest("GET", "/tasks")
        val response = MockHttpServletResponse()

        handler.commence(
            request,
            response,
            object : AuthenticationException("token ausente") {}
        )

        assertEquals(401, response.status)
        assertTrue(response.contentType?.startsWith("application/json") == true)
        val body = objectMapper.readTree(response.contentAsString)
        assertEquals(401, body["status"].asInt())
        assertEquals("Autenticación requerida", body["message"].asText())
        assertEquals("/tasks", body["path"].asText())
    }

    @Test
    fun `access denied responde 403 con formato ErrorResponse`() {
        val request = MockHttpServletRequest("GET", "/users")
        val response = MockHttpServletResponse()

        handler.handle(request, response, AccessDeniedException("sin permisos"))

        assertEquals(403, response.status)
        assertTrue(response.contentType?.startsWith("application/json") == true)
        val body = objectMapper.readTree(response.contentAsString)
        assertEquals(403, body["status"].asInt())
        assertEquals("No tienes permisos para realizar esta acción", body["message"].asText())
        assertEquals("/users", body["path"].asText())
    }
}
