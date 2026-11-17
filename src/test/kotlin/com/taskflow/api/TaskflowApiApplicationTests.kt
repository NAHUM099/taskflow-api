package com.taskflow.api

import com.taskflow.api.security.ratelimit.LoginAttemptStore
import com.taskflow.api.security.ratelimit.LoginAttemptTracker
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import javax.sql.DataSource

/**
 * Carga el contexto completo de la aplicación sin depender de PostgreSQL.
 *
 * Sustituye solo la base de datos por H2 en memoria (`ddl-auto: create-drop`) y
 * aporta el secreto JWT que en producción llega por variable de entorno. Así el
 * test valida de verdad el cableado de seguridad, JPA y rate limiting.
 */
@SpringBootTest
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:taskflow-context;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
    ]
)
class TaskflowApiApplicationTests {

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var loginAttemptStore: LoginAttemptStore

    @Test
    fun `el contexto arranca y crea el esquema en una base de datos en memoria`() {
        val tables = mutableSetOf<String>()

        dataSource.connection.use { connection ->
            assertTrue(connection.isValid(2), "La conexión con H2 no es válida")

            connection.metaData.getTables(null, null, "%", arrayOf("TABLE")).use { resultSet ->
                while (resultSet.next()) {
                    tables += resultSet.getString("TABLE_NAME").lowercase()
                }
            }
        }

        assertTrue(tables.containsAll(listOf("users", "tasks")), "Tablas creadas: $tables")
        // Sin LOGIN_RATE_LIMIT_STORE el rate limiting queda en memoria.
        assertInstanceOf(LoginAttemptTracker::class.java, loginAttemptStore)
    }
}
