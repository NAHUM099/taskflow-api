package com.taskflow.api.migration

import com.taskflow.api.TaskflowApiApplication
import org.flywaydb.core.api.FlywayException
import org.hibernate.tool.schema.spi.SchemaManagementException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder

/**
 * Escenarios de Flyway contra PostgreSQL real y aislado (bases `taskflow_it_*`
 * desechables, creadas y borradas por el propio test).
 *
 * Para ejecutarlo:
 * `TASKFLOW_PG_IT_URL=jdbc:postgresql://localhost:5434/postgres .\gradlew.bat test`
 * (con `DB_USERNAME` y `DB_PASSWORD` en el entorno). Sin esa variable el test se
 * omite, de modo que `.\gradlew.bat clean test` sigue sin necesitar PostgreSQL.
 */
@EnabledIfEnvironmentVariable(named = PostgresItSupport.URL_ENV, matches = ".+")
class FlywayMigrationScenariosTest {

    private val databases = mutableListOf<PostgresItSupport.IsolatedDatabase>()

    @AfterEach
    fun cleanUp() {
        databases.forEach { PostgresItSupport.dropIsolatedDatabase(it) }
        databases.clear()
    }

    @Test
    fun `una base nueva aplica V1 y registra el historial`() {
        val database = newDatabase()

        val result = PostgresItSupport.flyway(database).migrate()

        assertEquals(1, result.migrationsExecuted)
        assertEquals(listOf("1|SQL|true"), PostgresItSupport.history(database))
        assertTrue(PostgresItSupport.tableExists(database, "users"))
        assertTrue(PostgresItSupport.tableExists(database, "tasks"))

        // El checksum queda validado: repetir el arranque no reaplica nada.
        PostgresItSupport.flyway(database).validate()
        assertEquals(0, PostgresItSupport.flyway(database).migrate().migrationsExecuted)

        // La identidad funciona igual que en la base existente.
        PostgresItSupport.execute(
            database,
            "INSERT INTO users (email, name, password_hash, role) " +
                "VALUES ('nueva@taskflow.dev', 'Nueva', 'hash', 'USER')"
        )
        assertEquals("1", PostgresItSupport.scalar(database, "SELECT id FROM users"))
        assertEquals("USER", PostgresItSupport.scalar(database, "SELECT role FROM users"))
    }

    @Test
    fun `una base vacia con baselineOnMigrate no se marca como existente`() {
        val database = newDatabase()

        val result = PostgresItSupport.flyway(database, baselineOnMigrate = true).migrate()

        // Flyway solo hace baseline de esquemas NO vacíos: aquí aplica la V1.
        assertEquals(1, result.migrationsExecuted)
        assertEquals(listOf("1|SQL|true"), PostgresItSupport.history(database))
        assertTrue(PostgresItSupport.tableExists(database, "users"))
    }

    @Test
    fun `una base existente compatible se adopta con baseline y conserva los datos`() {
        val database = newDatabase()
        createLegacySchema(database)

        val result = PostgresItSupport.flyway(database, baselineOnMigrate = true).migrate()

        // No se ejecuta ningún DDL: V1 queda marcada como BASELINE.
        assertEquals(0, result.migrationsExecuted)
        assertEquals(listOf("1|BASELINE|true"), PostgresItSupport.history(database))
        assertEquals("2", PostgresItSupport.scalar(database, "SELECT count(*) FROM users"))
        assertEquals("2", PostgresItSupport.scalar(database, "SELECT count(*) FROM tasks"))
        assertEquals(
            "ana@taskflow.dev",
            PostgresItSupport.scalar(database, "SELECT email FROM users ORDER BY id LIMIT 1")
        )

        // A partir de aquí Flyway administra la base sin baseline adicional.
        assertEquals(0, PostgresItSupport.flyway(database).migrate().migrationsExecuted)
    }

    @Test
    fun `una base existente sin baseline falla de forma controlada y no modifica nada`() {
        val database = newDatabase()
        createLegacySchema(database)

        val error = assertThrows(FlywayException::class.java) {
            PostgresItSupport.flyway(database).migrate()
        }

        assertTrue(error.message.orEmpty().contains("non-empty", ignoreCase = true), error.message)
        assertEquals("2", PostgresItSupport.scalar(database, "SELECT count(*) FROM users"))
        assertEquals("2", PostgresItSupport.scalar(database, "SELECT count(*) FROM tasks"))
        assertFalse(PostgresItSupport.tableExists(database, "flyway_schema_history"))
    }

    @Test
    fun `las migraciones posteriores se aplican en orden`() {
        val database = newDatabase()

        PostgresItSupport.flyway(
            database,
            locations = listOf("classpath:db/migration", "classpath:db/testmigration")
        ).migrate()

        assertEquals(listOf("1|SQL|true", "2|SQL|true"), PostgresItSupport.history(database))
        assertTrue(PostgresItSupport.tableExists(database, "flyway_it_order_marker"))
    }

    @Test
    fun `un esquema incompatible detiene el arranque sin modificar el esquema`() {
        val database = newDatabase()
        PostgresItSupport.flyway(database).migrate()
        PostgresItSupport.execute(database, "ALTER TABLE tasks DROP COLUMN description")

        val failure = assertThrows(Exception::class.java) {
            // Los argumentos de línea de comandos tienen prioridad sobre
            // application.yaml, al contrario que .properties().
            SpringApplicationBuilder(TaskflowApiApplication::class.java)
                .web(WebApplicationType.NONE)
                .run(
                    "--spring.datasource.url=${database.url}",
                    "--spring.datasource.username=${PostgresItSupport.user()}",
                    "--spring.datasource.password=${PostgresItSupport.password()}",
                    "--jwt.secret=$TEST_JWT_SECRET",
                    "--spring.main.banner-mode=off",
                    "--logging.level.root=WARN"
                )
        }

        // El arranque falla por la validación de esquema de Hibernate.
        val causes = generateSequence(failure as Throwable) { it.cause }.toList()
        assertTrue(
            causes.any { it is SchemaManagementException },
            "Se esperaba SchemaManagementException, causas: ${causes.map { it::class.simpleName }}"
        )

        // Hibernate no reparó el esquema por su cuenta y el historial no cambió.
        assertEquals(
            "0",
            PostgresItSupport.scalar(
                database,
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' " +
                    "AND table_name = 'tasks' AND column_name = 'description'"
            )
        )
        assertEquals(listOf("1|SQL|true"), PostgresItSupport.history(database))
    }

    private fun newDatabase(): PostgresItSupport.IsolatedDatabase =
        PostgresItSupport.createIsolatedDatabase().also { databases += it }

    /** Crea el esquema "heredado" (el que dejó `ddl-auto: update`) con datos. */
    private fun createLegacySchema(database: PostgresItSupport.IsolatedDatabase) {
        PostgresItSupport.execute(database, *legacySchemaStatements())
        PostgresItSupport.execute(
            database,
            "INSERT INTO users (email, name, password_hash, role) VALUES " +
                "('ana@taskflow.dev', 'Ana', 'hash', 'ADMIN'), " +
                "('luis@taskflow.dev', 'Luis', 'hash', 'USER')",
            "INSERT INTO tasks (created_at, description, status, title, user_id) VALUES " +
                "(now(), 'demo', 'PENDING', 'tarea 1', 1), " +
                "(now(), 'demo', 'DONE', 'tarea 2', 2)"
        )
    }

    private fun legacySchemaStatements(): Array<String> {
        val resource = requireNotNull(
            javaClass.classLoader.getResource("db/migration/V1__initial_schema.sql")
        ) { "No se encontro V1__initial_schema.sql en el classpath de test" }

        return resource.readText()
            .lines()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toTypedArray()
    }

    private companion object {
        const val TEST_JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
    }
}
