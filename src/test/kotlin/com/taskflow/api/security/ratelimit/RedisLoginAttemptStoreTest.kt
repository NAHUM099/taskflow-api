package com.taskflow.api.security.ratelimit

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

/**
 * Verifica el almacén distribuido sin necesitar un Redis real: se sustituye
 * [StringRedisTemplate] por un mock para comprobar las operaciones que se
 * emiten (`INCR`, `EXPIRE`, `DEL`) y el *fail-open* ante una caída del caché.
 */
class RedisLoginAttemptStoreTest {

    private val redis: StringRedisTemplate = mock(StringRedisTemplate::class.java)

    @Suppress("UNCHECKED_CAST")
    private val values: ValueOperations<String, String> =
        mock(ValueOperations::class.java) as ValueOperations<String, String>

    private val properties = LoginRateLimitProperties(
        window = Duration.ofSeconds(300),
        maxAttemptsPerAccount = 3,
        maxAttemptsPerIp = 30
    )

    private val store = RedisLoginAttemptStore(redis, properties)

    @BeforeEach
    fun setUp() {
        `when`(redis.opsForValue()).thenReturn(values)
    }

    @Test
    fun `sin intentos previos deja pasar`() {
        `when`(values.get(ACCOUNT_KEY)).thenReturn(null)
        `when`(values.get(IP_KEY)).thenReturn(null)

        assertEquals(0, store.secondsUntilAllowed(IP, EMAIL))
    }

    @Test
    fun `por debajo del limite por cuenta deja pasar`() {
        `when`(values.get(ACCOUNT_KEY)).thenReturn("2")

        assertEquals(0, store.secondsUntilAllowed(IP, EMAIL))
    }

    @Test
    fun `al alcanzar el limite por cuenta devuelve el ttl restante`() {
        `when`(values.get(ACCOUNT_KEY)).thenReturn("3")
        `when`(values.get(IP_KEY)).thenReturn("3")
        `when`(redis.getExpire(ACCOUNT_KEY)).thenReturn(120L)

        assertEquals(120, store.secondsUntilAllowed(IP, EMAIL))
    }

    @Test
    fun `limita el total por ip aunque cambie el email`() {
        `when`(values.get(ACCOUNT_KEY)).thenReturn(null)
        `when`(values.get(IP_KEY)).thenReturn("30")
        `when`(redis.getExpire(IP_KEY)).thenReturn(45L)

        assertEquals(45, store.secondsUntilAllowed(IP, EMAIL))
    }

    @Test
    fun `si la clave no tiene ttl se asume la ventana completa`() {
        `when`(values.get(ACCOUNT_KEY)).thenReturn("3")
        `when`(values.get(IP_KEY)).thenReturn(null)
        `when`(redis.getExpire(ACCOUNT_KEY)).thenReturn(-1L)

        assertEquals(properties.window.seconds, store.secondsUntilAllowed(IP, EMAIL))
    }

    @Test
    fun `registrar un fallo incrementa ambos contadores y fija la expiracion al crear la ventana`() {
        `when`(values.increment(ACCOUNT_KEY)).thenReturn(1L)
        `when`(values.increment(IP_KEY)).thenReturn(1L)

        store.recordFailure(IP, EMAIL)

        verify(redis).expire(ACCOUNT_KEY, properties.window)
        verify(redis).expire(IP_KEY, properties.window)
    }

    @Test
    fun `no renueva la expiracion cuando la ventana ya estaba abierta`() {
        `when`(values.increment(ACCOUNT_KEY)).thenReturn(2L)
        `when`(values.increment(IP_KEY)).thenReturn(5L)

        store.recordFailure(IP, EMAIL)

        verify(redis, never()).expire(anyString(), any(Duration::class.java))
    }

    @Test
    fun `un login correcto solo libera la cuota de la cuenta`() {
        store.recordSuccess(IP, EMAIL)

        verify(redis).delete(ACCOUNT_KEY)
        verify(redis, never()).delete(IP_KEY)
    }

    @Test
    fun `normaliza el email para no eludir el limite cambiando mayusculas`() {
        `when`(values.get(ACCOUNT_KEY)).thenReturn("3")
        `when`(values.get(IP_KEY)).thenReturn(null)
        `when`(redis.getExpire(ACCOUNT_KEY)).thenReturn(60L)

        assertEquals(60, store.secondsUntilAllowed(IP, "  Ana@TaskFlow.dev  "))
    }

    @Test
    fun `si redis no responde deja pasar el login y no propaga el error`() {
        `when`(redis.opsForValue())
            .thenThrow(RedisConnectionFailureException("Redis caído"))

        assertEquals(0, store.secondsUntilAllowed(IP, EMAIL))
        assertDoesNotThrow { store.recordFailure(IP, EMAIL) }
        assertDoesNotThrow { store.recordSuccess(IP, EMAIL) }
    }

    private companion object {
        const val IP = "192.168.1.10"
        const val EMAIL = "ana@taskflow.dev"
        const val KEY_PREFIX = "taskflow:login-rate"
        const val ACCOUNT_KEY = "$KEY_PREFIX:account:$IP|$EMAIL"
        const val IP_KEY = "$KEY_PREFIX:ip:$IP"
    }
}
