# Pruebas

Estrategia y comandos de prueba del backend. Resumen de cifras en el [README](../README.md).

Para ejecutar las pruebas automatizadas:

```powershell
.\gradlew.bat clean test
```

Los tests no necesitan PostgreSQL ni Redis:

- `TaskflowApiApplicationTests` carga el contexto completo con **H2 en memoria**
  (`ddl-auto: create-drop`, `spring.flyway.enabled=false`) y comprueba que se crean las tablas
  `users` y `tasks`.
- El rate limiting se prueba con el almacén en memoria (`LoginAttemptTrackerTest`,
  `LoginRateLimiterTest`, `LoginBruteForceProtectionTest`) y con un `StringRedisTemplate` simulado
  (`RedisLoginAttemptStoreTest`), incluido el *fail-open* si Redis no responde.
- `GlobalExceptionHandlerTest` cubre el mapeo de cada excepción (incluida la traducción por
  `SQLState`) y `ExceptionHandlingEndpointTest` el contrato HTTP real a través de Spring MVC:
  validación de DTO y de parámetros, `409`, `403`, `401`, `500` sin datos internos.

## Tests de integración con PostgreSQL (Flyway)

Los escenarios que dependen de PostgreSQL real están en
`src/test/kotlin/com/taskflow/api/migration` y se **omiten** salvo que se indique la base de
mantenimiento en `TASKFLOW_PG_IT_URL`. El test crea y borra bases desechables `taskflow_it_*` y se
niega a ejecutarse si la URL no apunta a la base `postgres`:

```powershell
$env:TASKFLOW_PG_IT_URL = "jdbc:postgresql://localhost:5434/postgres"
.\gradlew.bat cleanTest test --tests "com.taskflow.api.migration.*"
```

Cubren: base nueva con `V1` y `flyway_schema_history`, base vacía con `baseline-on-migrate`
(no se marca como existente), adopción de una base existente con baseline conservando los datos,
base existente sin baseline (falla sin tocar nada), migraciones posteriores en orden, esquema
incompatible que detiene el arranque sin modificarlo, `validate` de Hibernate sobre el esquema
migrado y los errores reales de FK (`DELETE /users/{id}` → `409`) y UNIQUE.
