package com.taskflow.api.security.ratelimit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class ClientIpResolverTest {

    @Test
    fun `por defecto usa remoteAddr y no la cabecera`() {
        val request = request(REMOTE_ADDR, "1.2.3.4")

        assertEquals(REMOTE_ADDR, resolver(trustForwardedHeaders = false).resolve(request))
    }

    @Test
    fun `sin trust no exige proxies de confianza`() {
        val request = request(REMOTE_ADDR, "1.2.3.4")

        assertEquals(
            REMOTE_ADDR,
            resolver(trustForwardedHeaders = false, trustedProxies = emptyList()).resolve(request)
        )
    }

    @Test
    fun `trust activo exige al menos un proxy de confianza`() {
        assertThrows(IllegalArgumentException::class.java) {
            resolver(trustForwardedHeaders = true, trustedProxies = emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolver(trustForwardedHeaders = true, trustedProxies = listOf("   "))
        }
    }

    @Test
    fun `con trust activo usa la ultima IP que anadio el proxy inmediato`() {
        val request = request(REMOTE_ADDR, "1.2.3.4, 203.0.113.10")

        assertEquals(CLIENT_IP, resolver().resolve(request))
    }

    @Test
    fun `con proxies de confianza descarta los proxies de la cadena`() {
        val request = request(REMOTE_ADDR, "1.2.3.4, 203.0.113.10, 10.0.0.1")

        assertEquals(
            CLIENT_IP,
            resolver(trustedProxies = listOf(TRUSTED_PROXY)).resolve(request)
        )
    }

    @Test
    fun `con proxies de confianza acepta rangos CIDR suficientemente especificos`() {
        val request = request(
            remoteAddr = "10.0.0.5",
            forwardedFor = "1.2.3.4, 203.0.113.10, 10.0.0.1"
        )

        assertEquals(
            CLIENT_IP,
            resolver(trustedProxies = listOf("10.0.0.0/24")).resolve(request)
        )
    }

    @Test
    fun `rechaza rangos CIDR demasiado amplios`() {
        assertThrows(IllegalArgumentException::class.java) {
            resolver(trustedProxies = listOf("10.0.0.0/8"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolver(trustedProxies = listOf("0.0.0.0/0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolver(trustedProxies = listOf("2001:db8::/16"))
        }
    }

    @Test
    fun `rechaza un proxy de confianza que no es una IP literal`() {
        assertThrows(IllegalArgumentException::class.java) {
            resolver(trustedProxies = listOf("localhost"))
        }
    }

    @Test
    fun `un remoteAddr no valido no rompe la resolucion`() {
        val request = request("unknown", "1.2.3.4")

        assertEquals(
            "unknown",
            resolver(trustedProxies = listOf(TRUSTED_PROXY)).resolve(request)
        )
    }

    @Test
    fun `si la conexion no viene de un proxy de confianza ignora la cabecera`() {
        val request = request("198.51.100.20", "1.2.3.4")

        assertEquals(
            "198.51.100.20",
            resolver(trustedProxies = listOf(TRUSTED_PROXY)).resolve(request)
        )
    }

    @Test
    fun `una cabecera con una entrada invalida cae a remoteAddr`() {
        val request = request(REMOTE_ADDR, "1.2.3.4, no-es-una-ip")

        assertEquals(REMOTE_ADDR, resolver().resolve(request))
    }

    @Test
    fun `una cabecera con un hostname cae a remoteAddr`() {
        val request = request(REMOTE_ADDR, "1.2.3.4, localhost")

        assertEquals(REMOTE_ADDR, resolver().resolve(request))
    }

    @Test
    fun `si hay varias lineas de X-Forwarded-For se ignora la cabecera completa`() {
        val request = MockHttpServletRequest().apply {
            remoteAddr = REMOTE_ADDR
            addHeader(FORWARDED_FOR_HEADER, "1.2.3.4")
            addHeader(FORWARDED_FOR_HEADER, "203.0.113.10")
        }

        assertEquals(REMOTE_ADDR, resolver().resolve(request))
    }

    @Test
    fun `si toda la cadena son proxies de confianza cae a remoteAddr`() {
        val request = request(REMOTE_ADDR, "10.0.0.2, 10.0.0.1")

        assertEquals(
            REMOTE_ADDR,
            resolver(trustedProxies = listOf("10.0.0.0/24")).resolve(request)
        )
    }

    @Test
    fun `sin cabecera usa remoteAddr`() {
        val request = request(REMOTE_ADDR)

        assertEquals(REMOTE_ADDR, resolver().resolve(request))
    }

    @Test
    fun `admite IPv6`() {
        val request = request(REMOTE_ADDR, "2001:db8::10")

        assertEquals("2001:db8::10", resolver().resolve(request))
    }

    private fun resolver(
        trustForwardedHeaders: Boolean = true,
        trustedProxies: List<String> = listOf(TRUSTED_PROXY)
    ) = ClientIpResolver(
        LoginRateLimitProperties(
            trustForwardedHeaders = trustForwardedHeaders,
            trustedProxies = trustedProxies
        )
    )

    private fun request(remoteAddr: String, forwardedFor: String? = null) =
        MockHttpServletRequest().apply {
            this.remoteAddr = remoteAddr
            forwardedFor?.let { addHeader(FORWARDED_FOR_HEADER, it) }
        }

    private companion object {
        const val FORWARDED_FOR_HEADER = "X-Forwarded-For"
        const val REMOTE_ADDR = "10.0.0.1"
        const val TRUSTED_PROXY = "10.0.0.1"
        const val CLIENT_IP = "203.0.113.10"
    }
}
