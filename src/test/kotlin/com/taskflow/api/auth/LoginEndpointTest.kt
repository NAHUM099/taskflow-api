package com.taskflow.api.auth

import com.taskflow.api.exception.classException.TooManyLoginAttemptsException
import com.taskflow.api.security.JwtService
import com.taskflow.api.security.SecurityConfig
import com.taskflow.api.security.SecurityErrorHandler
import com.taskflow.api.security.TaskFlowUserDetailsService
import com.taskflow.api.security.ratelimit.ClientIpResolver
import com.taskflow.api.security.ratelimit.LoginRateLimitConfig
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Comprueba el contrato HTTP de `/auth/login`: sigue siendo público y, cuando
 * el rate limiter corta la petición, responde 429 con `Retry-After`.
 */
@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class, SecurityErrorHandler::class, ClientIpResolver::class, LoginRateLimitConfig::class)
class LoginEndpointTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authService: AuthService

    @MockitoBean
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var userDetailsService: TaskFlowUserDetailsService

    private val credentials = LoginRequest(EMAIL, PASSWORD)

    @Test
    fun `el login sigue siendo publico y devuelve el token`() {
        `when`(authService.login(credentials, LOCALHOST))
            .thenReturn(LoginResponse("jwt-de-prueba"))

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("jwt-de-prueba"))
    }

    @Test
    fun `cuota agotada responde 429 con Retry-After`() {
        `when`(authService.login(credentials, LOCALHOST))
            .thenThrow(TooManyLoginAttemptsException(30))

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().string("Retry-After", "30"))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(
                jsonPath("$.message")
                    .value("Demasiados intentos de inicio de sesión. Inténtalo de nuevo en 30 segundos.")
            )
    }

    private companion object {
        const val EMAIL = "ana@taskflow.dev"
        const val PASSWORD = "secreta123"
        const val LOCALHOST = "127.0.0.1"
        const val BODY = """{"email":"ana@taskflow.dev","password":"secreta123"}"""
    }
}
