package com.taskflow.api.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockHttpServletRequest

class CorsConfigurationTest {

    @Test
    fun `usa los origenes configurados y no acepta comodines`() {
        val properties = CorsProperties(
            listOf("http://localhost:3000", "https://frontend.example.com")
        )
        val securityConfig = SecurityConfig(
            mock(JwtAuthenticationFilter::class.java),
            SecurityErrorHandler(ObjectMapper().findAndRegisterModules()),
            properties
        )

        val configuration = requireNotNull(
            securityConfig.corsConfigurationSource()
                .getCorsConfiguration(MockHttpServletRequest("OPTIONS", "/tasks"))
        )

        assertEquals(properties.allowedOrigins, configuration.allowedOrigins)
        assertFalse(configuration.allowedOrigins.orEmpty().contains("*"))
        assertFalse(configuration.allowedMethods.orEmpty().contains("*"))
        assertFalse(configuration.allowedHeaders.orEmpty().contains("*"))
        assertEquals(listOf("Retry-After"), configuration.exposedHeaders)
    }

    @Test
    fun `rechaza origenes comodines o valores vacios`() {
        assertThrows(IllegalArgumentException::class.java) {
            CorsProperties(listOf("*"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CorsProperties(listOf(""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CorsProperties(emptyList())
        }
    }
}
