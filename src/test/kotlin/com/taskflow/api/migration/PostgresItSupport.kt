package com.taskflow.api.migration

import org.flywaydb.core.Flyway
import java.sql.DriverManager
import java.util.UUID

/**
 * Utilidades para ejecutar los escenarios de Flyway contra un PostgreSQL
 * aislado.
 *
 * La variable `TASKFLOW_PG_IT_URL` debe apuntar a la base de **mantenimiento**
 * (`postgres`) y solo se usa para crear y borrar bases desechables con prefijo
 * `taskflow_it_`. El test se niega a ejecutarse si la URL apunta a otra base,
 * de modo que la base real de TaskFlow nunca se toca.
 */
object PostgresItSupport {

    const val URL_ENV = "TASKFLOW_PG_IT_URL"

    private const val DATABASE_PREFIX = "taskflow_it_"
    private const val MAINTENANCE_DATABASE = "postgres"

    data class IsolatedDatabase(val name: String, val url: String)

    private fun maintenanceUrl(): String? = System.getenv(URL_ENV)?.trim()?.takeIf { it.isNotEmpty() }

    fun user(): String = requireEnv("DB_USERNAME")

    fun password(): String = requireEnv("DB_PASSWORD")

    private fun requireEnv(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: error("Falta la variable de entorno $name para los tests de PostgreSQL")

    fun createIsolatedDatabase(): IsolatedDatabase {
        val maintenance = requireNotNull(maintenanceUrl()) { "Falta la variable $URL_ENV" }
        val maintenanceDatabase = databaseName(maintenance)

        require(maintenanceDatabase == MAINTENANCE_DATABASE) {
            "$URL_ENV debe apuntar a la base de mantenimiento '$MAINTENANCE_DATABASE' " +
                "(se recibio '$maintenanceDatabase') para no tocar una base con datos"
        }

        val name = DATABASE_PREFIX + UUID.randomUUID().toString().replace("-", "").take(12)

        connect(maintenance).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE DATABASE $name")
            }
        }

        return IsolatedDatabase(name = name, url = withDatabase(maintenance, name))
    }

    fun dropIsolatedDatabase(database: IsolatedDatabase) {
        val maintenance = requireNotNull(maintenanceUrl()) { "Falta la variable $URL_ENV" }

        require(database.name.startsWith(DATABASE_PREFIX)) {
            "Solo se borran bases aisladas (${database.name})"
        }

        connect(maintenance).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
                        "WHERE datname = '${database.name}'"
                )
                statement.execute("DROP DATABASE IF EXISTS ${database.name}")
            }
        }
    }

    fun flyway(
        database: IsolatedDatabase,
        baselineOnMigrate: Boolean = false,
        locations: List<String> = listOf("classpath:db/migration")
    ): Flyway = Flyway.configure()
        .dataSource(database.url, user(), password())
        .locations(*locations.toTypedArray())
        .baselineOnMigrate(baselineOnMigrate)
        .baselineVersion("1")
        .baselineDescription("esquema previo a Flyway")
        .load()

    fun execute(database: IsolatedDatabase, vararg sql: String) {
        connect(database.url).use { connection ->
            connection.autoCommit = true
            connection.createStatement().use { statement ->
                sql.forEach { statement.execute(it) }
            }
        }
    }

    fun scalar(database: IsolatedDatabase, sql: String): String? =
        connect(database.url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    if (result.next()) result.getString(1) else null
                }
            }
        }

    /** Historial de Flyway como lista de `version|type|success`. */
    fun history(database: IsolatedDatabase): List<String> =
        connect(database.url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT version, type, success FROM flyway_schema_history ORDER BY installed_rank"
                ).use { result ->
                    buildList {
                        while (result.next()) {
                            add("${result.getString(1)}|${result.getString(2)}|${result.getBoolean(3)}")
                        }
                    }
                }
            }
        }

    fun tableExists(database: IsolatedDatabase, table: String): Boolean =
        scalar(
            database,
            "SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_name = '$table'"
        ) == "1"

    private fun connect(url: String) = DriverManager.getConnection(url, user(), password())

    private fun databaseName(url: String): String =
        url.substringAfterLast('/').substringBefore('?')

    private fun withDatabase(url: String, database: String): String {
        val prefix = url.substringBeforeLast('/')
        val query = url.substringAfterLast('/').substringAfter('?', "")
        return "$prefix/$database" + if (query.isEmpty()) "" else "?$query"
    }
}
