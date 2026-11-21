package com.taskflow.api.security

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableConfigurationProperties(CorsProperties::class)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val securityErrorHandler: SecurityErrorHandler,
    private val corsProperties: CorsProperties
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder  {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun authenticationProvider(
        userDetailsService: TaskFlowUserDetailsService,
        passwordEncoder: PasswordEncoder
    ) : AuthenticationProvider {

        return DaoAuthenticationProvider(userDetailsService).apply {
            setPasswordEncoder(passwordEncoder)
        }
    }

    @Bean
    fun authenticationManager(
        configuration: AuthenticationConfiguration
    ): AuthenticationManager {
        return configuration.authenticationManager
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        authenticationProvider: AuthenticationProvider
    ): SecurityFilterChain {

        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .authorizeHttpRequests {
                // Única ruta pública: el login. El alta de usuarios NO es pública:
                // crear cuentas exige un JWT con ROLE_ADMIN (ver regla siguiente).
                it.requestMatchers(HttpMethod.POST, "/auth/login").permitAll()

                // Módulo de usuarios (incluye POST /users): solo ADMIN.
                it.requestMatchers("/users/**").hasRole("ADMIN")

                //Creacion de tareas solo ADMIN y MODERATOR
                it.requestMatchers(HttpMethod.POST,"/tasks").hasAnyRole("ADMIN","MODERATOR")

                // Todo lo demas cerrado bajo llave
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                // 401 para peticiones sin autenticación válida (token ausente,
                // malformado, expirado o con firma inválida) y 403 para usuarios
                // autenticados sin permisos.
                it.authenticationEntryPoint(securityErrorHandler)
                it.accessDeniedHandler(securityErrorHandler)
            }
            .sessionManagement {
                // Le decimos a Spring que no guarde sesiones (state), todo lo manejará el JWT
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authenticationProvider(authenticationProvider)
            // Aquí inyectamos nuestro filtro justo antes del de Spring
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()

        // Los orígenes se configuran por entorno. No se usa "*": un origen
        // comodín permitiría que cualquier sitio llamara a la API con el JWT
        // del usuario desde el navegador.
        configuration.allowedOrigins = corsProperties.allowedOrigins.map { origin ->
            origin.trim().trimEnd('/')
        }
        configuration.allowedMethods = listOf(
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE",
            "OPTIONS"
        )
        configuration.allowedHeaders = listOf(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT
        )
        // El navegador solo puede leer Retry-After del 429 si el backend lo
        // expone explícitamente en CORS.
        configuration.exposedHeaders = listOf(HttpHeaders.RETRY_AFTER)

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)

        return source
    }
}
