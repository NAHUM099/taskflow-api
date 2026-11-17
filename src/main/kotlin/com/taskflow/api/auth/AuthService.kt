package com.taskflow.api.auth

import com.taskflow.api.security.JwtService
import com.taskflow.api.security.ratelimit.LoginRateLimiter
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service


@Service
class AuthService (
    private val authenticationManager : AuthenticationManager,
    private val jwtService : JwtService,
    private val loginRateLimiter : LoginRateLimiter
) {
    fun login(request : LoginRequest, clientIp : String) : LoginResponse {

        // Antes de tocar la base de datos comprobamos la cuota del origen.
        // Si se superó, se lanza TooManyLoginAttemptsException -> 429.
        loginRateLimiter.checkAllowed(clientIp, request.email)

        val authentication = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(
                    request.email,
                    request.password
                )
            )
        } catch (ex : AuthenticationException) {
            // Credenciales incorrectas o cuenta deshabilitada: cuenta el fallo
            // y deja que el manejo centralizado devuelva 401.
            loginRateLimiter.recordFailure(clientIp, request.email)
            throw ex
        }

        // Login correcto: libera la cuota de esa cuenta para este origen.
        loginRateLimiter.recordSuccess(clientIp, request.email)

        val userDetails = authentication.principal as UserDetails
        val token = jwtService.generateToken(userDetails)

        return LoginResponse(token = token)
    }
}