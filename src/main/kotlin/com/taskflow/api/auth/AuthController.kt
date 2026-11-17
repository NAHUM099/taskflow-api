package com.taskflow.api.auth

import com.taskflow.api.security.ratelimit.ClientIpResolver
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController
    (
    private val authService : AuthService,
    private val clientIpResolver : ClientIpResolver
)
{

    @PostMapping("/auth/login")
    fun postLogin (
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest
    ) : LoginResponse {
        // La IP identifica al origen para el rate limiting; el servicio no
        // depende del servlet, solo recibe el valor ya resuelto.
        return authService.login(request, clientIpResolver.resolve(httpRequest))
    }
}