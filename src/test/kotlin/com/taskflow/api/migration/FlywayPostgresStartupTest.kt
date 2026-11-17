package com.taskflow.api.migration

import com.taskflow.api.entity.UserEntity
import com.taskflow.api.repository.UserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Arranca la aplicación completa contra un PostgreSQL aislado creado por el
 * test: Flyway aplica V1 al arrancar, Hibernate valida el esquema resultante y
 * se comprueban los errores de integridad reales (clave foránea y UNIQUE).
 */
@EnabledIfEnvironmentVariable(named = PostgresItSupport.URL_ENV, matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
class FlywayPostgresStartupTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.update("DELETE FROM tasks")
        jdbcTemplate.update("DELETE FROM users")
    }

    @Test
    fun `flyway migra la base al arrancar y hibernate valida el esquema`() {
        val version = jdbcTemplate.queryForObject(
            "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
            String::class.java
        )
        val passwordColumn = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' " +
                "AND table_name = 'users' AND column_name = 'password_hash'",
            Int::class.java
        )

        assertEquals("1", version)
        // El nombre físico que espera Hibernate coincide con el de la migración.
        assertEquals(1, passwordColumn)
    }

    @Test
    fun `borrar un usuario con tareas devuelve 409 y conserva los datos`() {
        val userId = insertUser("ana@taskflow.dev")
        insertTask(userId)

        mockMvc.perform(delete("/users/$userId").with(user("admin@taskflow.dev").roles("ADMIN")))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(
                jsonPath("$.message")
                    .value("El usuario tiene tareas asociadas y no se puede eliminar")
            )

        // No se borró ni modificó nada: la transacción se revirtió.
        assertEquals(1, count("users"))
        assertEquals(1, count("tasks"))
    }

    @Test
    fun `crear un usuario con email duplicado devuelve 409`() {
        insertUser("ana@taskflow.dev")

        mockMvc.perform(
            post("/users")
                .with(user("admin@taskflow.dev").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Ana","email":"ana@taskflow.dev","password":"secreta123"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("email ya existente"))

        assertEquals(1, count("users"))
    }

    @Test
    fun `la restriccion UNIQUE de PostgreSQL se traduce a DataIntegrityViolationException`() {
        insertUser("ana@taskflow.dev")

        val duplicate = UserEntity(name = "Otra Ana", email = "ana@taskflow.dev", passwordHash = "hash")

        assertThrows(DataIntegrityViolationException::class.java) {
            userRepository.save(duplicate)
        }

        assertEquals(1, count("users"))
    }

    private fun insertUser(email: String): Long =
        requireNotNull(
            jdbcTemplate.queryForObject(
                "INSERT INTO users (email, name, password_hash, role) " +
                    "VALUES (?, 'Ana', 'hash', 'USER') RETURNING id",
                Long::class.java,
                email
            )
        )

    private fun insertTask(userId: Long): Long =
        requireNotNull(
            jdbcTemplate.queryForObject(
                "INSERT INTO tasks (created_at, description, status, title, user_id) " +
                    "VALUES (now(), 'demo', 'PENDING', 'tarea', ?) RETURNING id",
                Long::class.java,
                userId
            )
        )

    private fun count(table: String): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Int::class.java) ?: 0

    companion object {

        private lateinit var database: PostgresItSupport.IsolatedDatabase

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            database = PostgresItSupport.createIsolatedDatabase()
            registry.add("spring.datasource.url") { database.url }
            registry.add("spring.datasource.username") { PostgresItSupport.user() }
            registry.add("spring.datasource.password") { PostgresItSupport.password() }
            registry.add("jwt.secret") { TEST_JWT_SECRET }
        }

        @JvmStatic
        @AfterAll
        fun dropDatabase() {
            PostgresItSupport.dropIsolatedDatabase(database)
        }

        private const val TEST_JWT_SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
    }
}
