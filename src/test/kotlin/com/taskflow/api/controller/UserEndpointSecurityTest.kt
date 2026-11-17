package com.taskflow.api.controller

import com.taskflow.api.dto.user.CreateUserRequest
import com.taskflow.api.dto.user.UserResponse
import com.taskflow.api.security.JwtService
import com.taskflow.api.security.SecurityConfig
import com.taskflow.api.security.SecurityErrorHandler
import com.taskflow.api.security.TaskFlowUserDetailsService
import com.taskflow.api.service.TaskService
import com.taskflow.api.service.UserService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Verifica la política real de `POST /users` a través del filtro de seguridad:
 * sin autenticación (401), autenticado sin rol ADMIN (403) y ADMIN (200).
 */
@WebMvcTest(UserController::class)
@Import(SecurityConfig::class, SecurityErrorHandler::class)
class UserEndpointSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var userService: UserService

    @MockitoBean
    private lateinit var taskService: TaskService

    @MockitoBean
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var userDetailsService: TaskFlowUserDetailsService

    private val createRequest = CreateUserRequest("Ana Torres", EMAIL, "secreta123")

    @Test
    fun `sin autenticacion responde 401 y no crea la cuenta`() {
        mockMvc.perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))

        verifyNoInteractions(userService)
    }

    @Test
    fun `un USER autenticado recibe 403`() {
        mockMvc.perform(
            post("/users")
                .with(user(ADMIN_EMAIL).roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))

        verifyNoInteractions(userService)
    }

    @Test
    fun `un MODERATOR autenticado recibe 403`() {
        mockMvc.perform(
            post("/users")
                .with(user(ADMIN_EMAIL).roles("MODERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
        )
            .andExpect(status().isForbidden)

        verifyNoInteractions(userService)
    }

    @Test
    fun `un ADMIN puede crear cuentas`() {
        `when`(userService.createUser(createRequest))
            .thenReturn(UserResponse(10, "Ana Torres", EMAIL, true))

        mockMvc.perform(
            post("/users")
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(EMAIL))

        verify(userService).createUser(createRequest)
    }

    @Test
    fun `el rol enviado en el body se ignora`() {
        `when`(userService.createUser(createRequest))
            .thenReturn(UserResponse(11, "Ana Torres", EMAIL, true))

        mockMvc.perform(
            post("/users")
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"Ana Torres","email":"$EMAIL","password":"secreta123","role":"ADMIN","active":true}"""
                )
        )
            .andExpect(status().isOk)

        // CreateUserRequest no tiene campo role: el request que llega al
        // servicio es idéntico y el rol real lo decide el backend (ROLE_USER).
        verify(userService).createUser(createRequest)
    }

    private companion object {
        const val EMAIL = "ana@taskflow.dev"
        const val ADMIN_EMAIL = "admin@taskflow.dev"
        const val BODY = """{"name":"Ana Torres","email":"ana@taskflow.dev","password":"secreta123"}"""
    }
}
