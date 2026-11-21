package com.taskflow.api.security.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.function.Supplier

/**
 * Comprueba la selección del almacén según
 * `taskflow.security.login-rate-limit.store`, sin levantar la aplicación entera.
 */
class LoginRateLimitConfigTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(LoginRateLimitConfig::class.java)
        .withBean(
            StringRedisTemplate::class.java,
            Supplier { mock(StringRedisTemplate::class.java) }
        )

    @Test
    fun `por defecto el almacen es en memoria`() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBean(LoginAttemptStore::class.java))
                .isInstanceOf(LoginAttemptTracker::class.java)
        }
    }

    @Test
    fun `con store=redis usa el almacen compartido`() {
        contextRunner
            .withPropertyValues("${LoginRateLimitProperties.PREFIX}.store=redis")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBean(LoginAttemptStore::class.java))
                    .isInstanceOf(RedisLoginAttemptStore::class.java)
            }
    }

    @Test
    fun `con trust activo y trusted-proxies vacio el contexto falla`() {
        contextRunner
            .withPropertyValues(
                "${LoginRateLimitProperties.PREFIX}.trust-forwarded-headers=true",
                "${LoginRateLimitProperties.PREFIX}.trusted-proxies="
            )
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .hasStackTraceContaining("trusted-proxies")
            }
    }
}
