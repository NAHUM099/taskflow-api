package com.taskflow.api.auth

import com.taskflow.api.security.JwtService
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service


@Service
class AuthService (
    private val authenticationManager : AuthenticationManager,
    private val jwtService : JwtService
) {
    fun login(request : LoginRequest) : LoginResponse {

        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(
                request.email,
                request.password
            )
        )

        val userDetails = authentication.principal as UserDetails
        val token = jwtService.generateToken(userDetails)

        return LoginResponse(token = token)
    }
}