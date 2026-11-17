package com.taskflow.api.auth

import com.taskflow.api.security.JwtService
import com.taskflow.api.security.SecurityConfig
import com.taskflow.api.security.SecurityErrorHandler
import com.taskflow.api.security.TaskFlowUserDetailsService
import com.taskflow.api.security.ratelimit.ClientIpResolver
import com.taskflow.api.security.ratelimit.LoginRateLimitConfig
import com.taskflow.api.security.ratelimit.LoginRateLimiter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Prueba el rate limiting con el [LoginRateLimiter] y [AuthService] reales,
 * sustituyendo solo la autenticación (sin base de datos).
 *
 * Con `max-attempts` en 2: los dos primeros fallos devuelven 401 y el tercero
 * 429. Además comprueba que el bloqueo es del origen y no de la cuenta.
 */
@WebMvcTest(AuthController::class)
@Import(
    SecurityConfig::class,
    SecurityErrorHandler::class,
    AuthService::class,
    LoginRateLimiter::class,
    LoginRateLimitConfig::class,
    ClientIpResolver::class
)
@TestPropertySource(
    properties = [
        "taskflow.security.login-rate-limit.max-attempts-per-account=2",
        "taskflow.security.login-rate-limit.max-attempts-per-ip=2"
    ]
)
class LoginBruteForceProtectionTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authenticationManager: AuthenticationManager

    @MockitoBean
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var userDetailsService: TaskFlowUserDetailsService

    @BeforeEach
    fun setUp() {
        `when`(authenticationManager.authenticate(any()))
            .thenThrow(BadCredentialsException("credenciales incorrectas"))
    }

    @Test
    fun `los intentos repetidos con credenciales invalidas terminan en 429`() {
        repeat(2) {
            mockMvc.perform(login(BRUTE_FORCED_EMAIL, ATTACKER_IP))
                .andExpect(status().isUnauthorized)
        }

        mockMvc.perform(login(BRUTE_FORCED_EMAIL, ATTACKER_IP))
            .andExpect(status().isTooManyRequests)
    }

    @Test
    fun `bloquear un origen no bloquea la cuenta para otro origen`() {
        repeat(2) {
            mockMvc.perform(login(VICTIM_EMAIL, SECOND_ATTACKER_IP))
                .andExpect(status().isUnauthorized)
        }
        mockMvc.perform(login(VICTIM_EMAIL, SECOND_ATTACKER_IP))
            .andExpect(status().isTooManyRequests)

        // Otra IP con las mismas credenciales sigue recibiendo 401, no 429:
        // el bloqueo pertenece al origen, no a la cuenta.
        mockMvc.perform(login(VICTIM_EMAIL, OTHER_IP))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `un login correcto devuelve el token`() {
        val userDetails = User(
            VALID_EMAIL,
            "hash-de-prueba",
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        // doReturn evita invocar el método ya estubado con thenThrow en @BeforeEach.
        doReturn(UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities))
            .`when`(authenticationManager).authenticate(any())
        `when`(jwtService.generateToken(userDetails)).thenReturn("jwt-de-prueba")

        mockMvc.perform(login(VALID_EMAIL, VALID_IP))
            .andExpect(status().isOk)
    }

    private fun login(email: String, ip: String) =
        post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"secreta123"}""")
            .with(
                RequestPostProcessor { request ->
                    request.remoteAddr = ip
                    request
                }
            )

    private companion object {
        const val BRUTE_FORCED_EMAIL = "bruteforce@taskflow.dev"
        const val VICTIM_EMAIL = "victima@taskflow.dev"
        const val VALID_EMAIL = "ok@taskflow.dev"
        const val ATTACKER_IP = "10.0.0.1"
        const val SECOND_ATTACKER_IP = "10.0.0.2"
        const val OTHER_IP = "10.8.8.8"
        const val VALID_IP = "10.7.7.7"
    }
}
