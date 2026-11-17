package com.taskflow.api.security.ratelimit

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Registra la configuración del rate limiting de forma explícita.
 *
 * Se prefiere `@EnableConfigurationProperties` frente a
 * `@ConfigurationPropertiesScan` para que el registro funcione también en los
 * slices de test (`@WebMvcTest`), donde el escaneo global no se aplica.
 *
 * El almacén de contadores se elige con `taskflow.security.login-rate-limit.store`:
 * `memory` (por defecto) o `redis` para compartir el estado entre réplicas.
 */
@Configuration
@EnableConfigurationProperties(LoginRateLimitProperties::class)
class LoginRateLimitConfig {

    @Bean
    @ConditionalOnProperty(
        prefix = LoginRateLimitProperties.PREFIX,
        name = ["store"],
        havingValue = "redis"
    )
    fun redisLoginAttemptStore(
        redis: StringRedisTemplate,
        properties: LoginRateLimitProperties
    ): LoginAttemptStore = RedisLoginAttemptStore(redis, properties)

    @Bean
    @ConditionalOnProperty(
        prefix = LoginRateLimitProperties.PREFIX,
        name = ["store"],
        havingValue = "memory",
        matchIfMissing = true
    )
    fun inMemoryLoginAttemptStore(properties: LoginRateLimitProperties): LoginAttemptStore =
        LoginAttemptTracker(
            windowMillis = properties.window.toMillis(),
            maxAttemptsPerAccount = properties.maxAttemptsPerAccount,
            maxAttemptsPerIp = properties.maxAttemptsPerIp
        )
}

