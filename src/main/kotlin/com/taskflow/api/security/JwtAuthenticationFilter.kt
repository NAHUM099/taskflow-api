package com.taskflow.api.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userDetailsService: TaskFlowUserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        // Si no trae el header o no empieza con "Bearer ", lo dejamos pasar.
        // Las rutas públicas no lo necesitan, y las privadas tirarán 403 si llega acá vacío.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val jwt = authHeader.substring(7)
        val username = try {
            jwtService.extractUsername(jwt)
        } catch (e: Exception) {
            // Si el token es inválido o expiró, atrapamos el error
            null
        }

        // Si tenemos un usuario pero todavía no está autenticado en el contexto de Spring...
        if (username != null && SecurityContextHolder.getContext().authentication == null) {
            val userDetails: UserDetails = userDetailsService.loadUserByUsername(username)

            // Validamos contra nuestro JwtService
            if (jwtService.isTokenValid(jwt, userDetails)) {
                // Creamos el token de autenticación para Spring
                val authToken = UsernamePasswordAuthenticationToken(
                    userDetails,
                    null, // No pasamos contraseña
                    userDetails.authorities
                )
                
                // Le inyectamos los detalles de la petición (IP, session id, etc.)
                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                
                // Lo guardamos en el contexto de seguridad (¡Usuario autenticado!)
                SecurityContextHolder.getContext().authentication = authToken
            }
        }

        // Continuamos con el resto de la cadena de filtros
        filterChain.doFilter(request, response)
    }
}
