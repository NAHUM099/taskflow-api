package com.taskflow.api.auth

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController
    ( private val authService : AuthService)
{

    @PostMapping("/auth/login")
    fun postLogin (
        @Valid @RequestBody request: LoginRequest
    ) : LoginResponse {
        return authService.login(request)
    }
}