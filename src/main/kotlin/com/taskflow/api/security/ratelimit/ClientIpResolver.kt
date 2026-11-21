package com.taskflow.api.security.ratelimit

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.stereotype.Component
import java.util.Collections

/**
 * Resuelve la IP del cliente usada como clave del rate limiting.
 *
 * Por defecto se usa `remoteAddr`, que no puede falsificar el cliente. Detrás
 * de un proxy de confianza se puede activar `trust-forwarded-headers` para leer
 * `X-Forwarded-For`; en ese caso `trusted-proxies` es obligatorio y el bean no
 * arranca si está vacío. El header se interpreta de derecha a izquierda porque
 * cada proxy añade al final la IP del par que lo llamó; así los valores que el
 * cliente haya podido enviar al principio no se usan como identidad.
 *
 * Si hay varias líneas `X-Forwarded-For`, o cualquier entrada no es una IP
 * literal válida, se ignora la cabecera completa y se usa `remoteAddr`. Si toda
 * la cadena son proxies de confianza, también se usa `remoteAddr` (con un WARN)
 * para no colapsar a todos los clientes en una única clave falsa.
 *
 * Activar `trust-forwarded-headers` sin un proxy delante sigue permitiendo
 * falsificar el header, por eso el valor por defecto es `false`.
 */
@Component
class ClientIpResolver(
    private val properties: LoginRateLimitProperties
) {

    private val logger = LoggerFactory.getLogger(ClientIpResolver::class.java)

    private val trustedProxies: List<IpAddressMatcher> = properties.trustedProxies
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { value -> trustedProxyMatcher(value) }
        .toList()

    fun resolve(request: HttpServletRequest): String {
        val remoteAddr = request.remoteAddr
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: UNKNOWN_IP

        if (!properties.trustForwardedHeaders) {
            return remoteAddr
        }

        // La lista es obligatoria cuando trustForwardedHeaders=true (lo valida
        // LoginRateLimitProperties). Un cliente directo no puede saltarse el
        // límite enviando su propio X-Forwarded-For.
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr
        }

        val forwardedForHeaders = Collections.list(request.getHeaders(FORWARDED_FOR_HEADER))
        if (forwardedForHeaders.isEmpty()) {
            return remoteAddr
        }

        // Varias líneas del mismo header no tienen un orden fiable entre
        // proxies. Se rechaza la cabecera completa en lugar de elegir una.
        if (forwardedForHeaders.size > 1) {
            logger.warn(
                "Se ignoran {} líneas de {}; se usa remoteAddr={}",
                forwardedForHeaders.size,
                FORWARDED_FOR_HEADER,
                remoteAddr
            )
            return remoteAddr
        }

        val chain = parseForwardedFor(forwardedForHeaders.single())
        if (chain == null) {
            logger.warn(
                "Se ignora {} porque contiene entradas no válidas; se usa remoteAddr={}",
                FORWARDED_FOR_HEADER,
                remoteAddr
            )
            return remoteAddr
        }

        // Se descartan los proxies de confianza de derecha a izquierda y se
        // devuelve la primera IP no confiable. Si toda la cadena es de
        // confianza, no hay una identidad de cliente fiable: se usa remoteAddr.
        val clientIp = chain.asReversed().firstOrNull { !isTrustedProxy(it) }
        if (clientIp == null) {
            logger.warn(
                "{} solo contiene proxies de confianza; se usa remoteAddr={}",
                FORWARDED_FOR_HEADER,
                remoteAddr
            )
            return remoteAddr
        }

        return clientIp
    }

    /**
     * Devuelve la cadena completa o `null` si algún elemento no es una IP
     * literal válida. No se filtran entradas inválidas: una cadena con basura
     * se descarta entera (fail-closed) para no confiar en un prefijo elegido
     * por el atacante.
     */
    private fun parseForwardedFor(forwardedFor: String): List<String>? {
        val chain = forwardedFor
            .split(',')
            .map(String::trim)

        if (chain.isEmpty() || chain.any { value -> value.isEmpty() || !isIpAddress(value) }) {
            return null
        }

        return chain
    }

    private fun isTrustedProxy(address: String): Boolean {
        if (!isIpAddress(address)) {
            return false
        }

        return trustedProxies.any { proxy ->
            try {
                proxy.matches(address)
            } catch (ex: IllegalArgumentException) {
                false
            }
        }
    }

    private fun isIpAddress(value: String): Boolean {
        if (value.contains('/') || !isIpLiteral(value)) {
            return false
        }

        return canCreateMatcher(value)
    }

    /**
     * `IpAddressMatcher` usa `InetAddress.getByName`, que resolvería un
     * hostname. Para aceptar solo IPs literales se comprueba primero la forma:
     * IPv4 con cuatro octetos decimales o IPv6 con caracteres hexadecimales,
     * dos puntos y, opcionalmente, IPv4 embebida.
     */
    private fun isIpLiteral(value: String): Boolean =
        if (value.contains(':')) {
            IPV6_LITERAL.matches(value)
        } else {
            IPV4_LITERAL.matches(value)
        }

    private fun canCreateMatcher(value: String): Boolean =
        try {
            IpAddressMatcher(value)
            true
        } catch (ex: IllegalArgumentException) {
            false
        }

    private fun trustedProxyMatcher(value: String): IpAddressMatcher {
        val address = value.substringBefore('/')
        if (!isIpLiteral(address)) {
            throw IllegalArgumentException(
                "Proxy de confianza no válido en taskflow.security.login-rate-limit.trusted-proxies: '$value'"
            )
        }

        val matcher = try {
            IpAddressMatcher(value)
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Proxy de confianza no válido en taskflow.security.login-rate-limit.trusted-proxies: '$value'",
                ex
            )
        }

        validateCidrWidth(value, address)
        return matcher
    }

    /**
     * Evita rangos de confianza demasiado amplios: un /8 de IPv4 o un /0 de
     * IPv6 incluirían clientes normales y convertirían el proxy de confianza
     * en una vía para falsificar la identidad.
     */
    private fun validateCidrWidth(value: String, address: String) {
        val slashIndex = value.indexOf('/')
        if (slashIndex < 0) {
            return
        }

        val prefixLength = value.substring(slashIndex + 1).toIntOrNull()
            ?: throw IllegalArgumentException(
                "Prefijo CIDR no válido en taskflow.security.login-rate-limit.trusted-proxies: '$value'"
            )
        val isIpv6 = address.contains(':')
        val minimum = if (isIpv6) MIN_IPV6_PREFIX_LENGTH else MIN_IPV4_PREFIX_LENGTH
        val maximum = if (isIpv6) MAX_IPV6_PREFIX_LENGTH else MAX_IPV4_PREFIX_LENGTH

        if (prefixLength < minimum) {
            throw IllegalArgumentException(
                "Rango de proxy demasiado amplio en taskflow.security.login-rate-limit.trusted-proxies: " +
                    "'$value' (mínimo /$minimum)"
            )
        }
        if (prefixLength > maximum) {
            throw IllegalArgumentException(
                "Prefijo CIDR no válido en taskflow.security.login-rate-limit.trusted-proxies: '$value'"
            )
        }
    }

    private companion object {
        const val FORWARDED_FOR_HEADER = "X-Forwarded-For"
        const val UNKNOWN_IP = "unknown"

        const val MIN_IPV4_PREFIX_LENGTH = 16
        const val MIN_IPV6_PREFIX_LENGTH = 32
        const val MAX_IPV4_PREFIX_LENGTH = 32
        const val MAX_IPV6_PREFIX_LENGTH = 128

        val IPV4_LITERAL = Regex(
            "^(?:(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}" +
                "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$"
        )
        val IPV6_LITERAL = Regex("^[0-9A-Fa-f:.]+$")
    }
}
