package com.taskflow.api.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskflow.api.exception.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Traduce los fallos de seguridad de Spring Security a respuestas JSON con el
 * formato [ErrorResponse] del backend.
 *
 * - [AuthenticationEntryPoint]: 401 cuando la petición no lleva autenticación
 *   válida (token ausente, malformado, expirado o con firma inválida).
 * - [AccessDeniedHandler]: 403 cuando el usuario está autenticado pero no tiene
 *   permisos suficientes.
 */
@Component
class SecurityErrorHandler(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint, AccessDeniedHandler {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        writeError(
            request = request,
            response = response,
            status = HttpStatus.UNAUTHORIZED,
            message = "Autenticación requerida"
        )
    }

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        writeError(
            request = request,
            response = response,
            status = HttpStatus.FORBIDDEN,
            message = "No tienes permisos para realizar esta acción"
        )
    }

    private fun writeError(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: HttpStatus,
        message: String
    ) {
        val error = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = status.value(),
            message = message,
            path = request.requestURI
        )

        response.status = status.value()
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write(objectMapper.writeValueAsString(error))
    }
}
