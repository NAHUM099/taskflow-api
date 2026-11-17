package com.taskflow.api.auth

import com.taskflow.api.exception.classException.TooManyLoginAttemptsException
import com.taskflow.api.security.JwtService
import com.taskflow.api.security.ratelimit.LoginRateLimiter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails

class AuthServiceTest {

    private val authenticationManager: AuthenticationManager = mock(AuthenticationManager::class.java)
    private val jwtService: JwtService = mock(JwtService::class.java)
    private val loginRateLimiter: LoginRateLimiter = mock(LoginRateLimiter::class.java)
    private val authService = AuthService(authenticationManager, jwtService, loginRateLimiter)

    private val request = LoginRequest(EMAIL, "secreta123")

    @Test
    fun `login correcto devuelve el token y libera la cuota`() {
        val userDetails = userDetails()
        `when`(authenticationManager.authenticate(any()))
            .thenReturn(UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities))
        `when`(jwtService.generateToken(userDetails)).thenReturn("jwt-de-prueba")

        val response = authService.login(request, IP)

        assertEquals("jwt-de-prueba", response.token)
        verify(loginRateLimiter).checkAllowed(IP, EMAIL)
        verify(loginRateLimiter).recordSuccess(IP, EMAIL)
        verify(loginRateLimiter, never()).recordFailure(IP, EMAIL)
    }

    @Test
    fun `credenciales invalidas registran el fallo y propagan el error`() {
        `when`(authenticationManager.authenticate(any()))
            .thenThrow(BadCredentialsException("credenciales incorrectas"))

        assertThrows(BadCredentialsException::class.java) {
            authService.login(request, IP)
        }

        verify(loginRateLimiter).recordFailure(IP, EMAIL)
        verify(loginRateLimiter, never()).recordSuccess(IP, EMAIL)
        verifyNoInteractions(jwtService)
    }

    @Test
    fun `si la cuota esta agotada no se consulta la autenticacion`() {
        doThrow(TooManyLoginAttemptsException(30))
            .`when`(loginRateLimiter).checkAllowed(IP, EMAIL)

        val ex = assertThrows(TooManyLoginAttemptsException::class.java) {
            authService.login(request, IP)
        }

        assertEquals(30, ex.retryAfterSeconds)
        verify(authenticationManager, never()).authenticate(any())
        verify(loginRateLimiter, never()).recordFailure(IP, EMAIL)
    }

    private fun userDetails(): UserDetails = User(
        EMAIL,
        "hash-de-prueba",
        listOf(SimpleGrantedAuthority("ROLE_USER"))
    )

    private companion object {
        const val IP = "192.168.1.10"
        const val EMAIL = "ana@taskflow.dev"
    }
}
