package com.taskflow.api.security

import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
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
        // Las rutas públicas no lo necesitan; las privadas terminarán en 401 vía AuthenticationEntryPoint.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val jwt = authHeader.substring(7)

        try {
            val username = jwtService.extractUsername(jwt)

            // Si todavía no está autenticado en el contexto de Spring, cargamos el
            // usuario asociado al subject del token.
            if (SecurityContextHolder.getContext().authentication == null) {
                val userDetails: UserDetails = userDetailsService.loadUserByUsername(username)

                // Un usuario inactivo no debe autenticarse aunque el token siga vigente.
                // Validamos firma y expiración contra nuestro JwtService.
                if (userDetails.isEnabled && jwtService.isTokenValid(jwt, userDetails)) {
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
        } catch (e: JwtException) {
            // Token malformado, expirado o con firma inválida: no se autentica.
            // El AuthenticationEntryPoint responderá 401 en los endpoints protegidos.
            SecurityContextHolder.clearContext()
        } catch (e: AuthenticationException) {
            // Usuario inexistente en la base de datos: no debe producir un 500.
            SecurityContextHolder.clearContext()
        }

        // Continuamos con el resto de la cadena de filtros
        filterChain.doFilter(request, response)
    }
}
