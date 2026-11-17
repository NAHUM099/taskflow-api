package com.taskflow.api.security

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.security.SignatureException
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException

class JwtAuthenticationFilterTest {

    private val jwtService: JwtService = mock(JwtService::class.java)
    private val userDetailsService: TaskFlowUserDetailsService = mock(TaskFlowUserDetailsService::class.java)
    private val filter = JwtAuthenticationFilter(jwtService, userDetailsService)

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `sin header Authorization deja pasar sin autenticar`() {
        val request = MockHttpServletRequest("GET", "/tasks")
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `token malformado no autentica y no lanza excepcion`() {
        val request = requestConToken("Bearer token.malformado")
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        `when`(jwtService.extractUsername("token.malformado"))
            .thenThrow(MalformedJwtException("malformado"))

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `token expirado no autentica`() {
        val request = requestConToken("Bearer token.expirado")
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        `when`(jwtService.extractUsername("token.expirado"))
            .thenThrow(ExpiredJwtException(null, null, "expirado"))

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `token con firma invalida no autentica`() {
        val request = requestConToken("Bearer token.firma")
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        `when`(jwtService.extractUsername("token.firma"))
            .thenThrow(SignatureException("firma inválida"))

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `token valido y usuario activo establece la autenticacion`() {
        val request = requestConToken("Bearer token.valido")
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)
        val userDetails = mock(UserDetails::class.java)

        `when`(jwtService.extractUsername("token.valido")).thenReturn("ana@taskflow.dev")
        `when`(userDetailsService.loadUserByUsername("ana@taskflow.dev")).thenReturn(userDetails)
        `when`(userDetails.isEnabled).thenReturn(true)
        `when`(userDetails.authorities).thenReturn(listOf(SimpleGrantedAuthority("ROLE_USER")))
        `when`(jwtService.isTokenValid("token.valido", userDetails)).thenReturn(true)

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
        val authentication = requireNotNull(SecurityContextHolder.getContext().authentication)
        assertEquals(userDetails, authentication.principal)
    }

    @Test
    fun `token valido pero usuario inactivo no autentica`() {
        val request = requestConToken("Bearer token.valido")
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)
        val userDetails = mock(UserDetails::class.java)

        `when`(jwtService.extractUsername("token.valido")).thenReturn("ana@taskflow.dev")
        `when`(userDetailsService.loadUserByUsername("ana@taskflow.dev")).thenReturn(userDetails)
        `when`(userDetails.isEnabled).thenReturn(false)

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
        assertNull(SecurityContextHolder.getContext().authentication)
        verify(jwtService, never()).isTokenValid("token.valido", userDetails)
    }

    @Test
    fun `token valido pero usuario inexistente no autentica y no lanza excepcion`() {
        val request = requestConToken("Bearer token.valido")
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        `when`(jwtService.extractUsername("token.valido")).thenReturn("fantasma@taskflow.dev")
        `when`(userDetailsService.loadUserByUsername("fantasma@taskflow.dev"))
            .thenThrow(UsernameNotFoundException("no existe"))

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    private fun requestConToken(header: String): MockHttpServletRequest {
        val request = MockHttpServletRequest("GET", "/tasks")
        request.addHeader("Authorization", header)
        return request
    }
}
