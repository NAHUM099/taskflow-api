# TaskFlow API

API REST para la gestión de tareas, usuarios y autenticación del proyecto TaskFlow.

## Tecnologías

- Kotlin
- Spring Boot
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway (migraciones versionadas del esquema)
- JWT
- Gradle
- Docker y Jenkins para integración continua

## Funcionalidades

- Gestión de usuarios.
- Autenticación mediante JWT.
- Gestión de tareas.
- Aislamiento de tareas por rol: `USER` solo consulta y mueve sus tareas; `ADMIN` y `MODERATOR` ven todas.
- Actualización del estado de las tareas.
- Control de acceso según roles.

## Seguridad

### Reglas de acceso

- `POST /auth/login` es la única ruta pública.
- `/users/**`, incluido `POST /users`, requiere `ROLE_ADMIN`: **el registro público está deshabilitado**.
- `POST /tasks` requiere `ROLE_ADMIN` o `ROLE_MODERATOR`.
- Cualquier otra ruta requiere un JWT válido.
- Las respuestas de seguridad respetan el formato `ErrorResponse`: `401` cuando la petición no
  lleva autenticación válida (token ausente, malformado, expirado o de usuario inactivo) y `403`
  cuando el usuario está autenticado pero no tiene permisos.

### Alta de usuarios (`POST /users`)

Solo un `ADMIN` autenticado puede crear cuentas. El cuerpo (`CreateUserRequest`) no incluye el
campo `role`: si el cliente envía `role` en el JSON se ignora y el backend asigna siempre
`ROLE_USER` (`UserMapper.toEntity`). La promoción a otro rol se hace fuera de la API (base de
datos), nunca manipulando la petición. Se mantienen las validaciones (`@NotBlank`, `@Email`,
`@Size(min = 6)`) y la unicidad del correo (`409 Conflict`).

### Rate limiting del login

`/auth/login` limita los intentos fallidos con una ventana fija. Se usan dos contadores
independientes:

- `(IP, email)`: `max-attempts-per-account` intentos fallidos por ventana.
- `IP`: `max-attempts-per-ip` intentos fallidos por ventana, aunque cambie el email.

Al superar el límite se responde `429 Too Many Requests` con header `Retry-After` y un
`ErrorResponse` genérico, sin revelar si la cuenta existe ni su estado. La ventana caduca sola, de
modo que el bloqueo siempre es temporal. Un login correcto libera el contador de la pareja
`(IP, email)`; el contador por IP no se reinicia para que un atacante con cuenta propia no recupere
su cuota. La clave nunca es solo el email, así que un tercero no puede bloquear la cuenta de otra
persona desde su propia IP.

El almacén de los contadores es intercambiable (`LoginAttemptStore`):

| `store` | Implementación | Cuándo usarlo |
| --- | --- | --- |
| `memory` (por defecto) | `LoginAttemptTracker` | Una sola instancia; sin infraestructura extra |
| `redis` | `RedisLoginAttemptStore` | Varias réplicas: el límite pasa a ser global (`INCR` + `EXPIRE`) |

Configuración en `application.yaml`, ajustable por entorno:

| Variable | Por defecto | Descripción |
| --- | --- | --- |
| `LOGIN_RATE_LIMIT_ENABLED` | `true` | Activa o desactiva el límite |
| `LOGIN_RATE_LIMIT_WINDOW` | `5m` | Tamaño de la ventana (`Duration`: `30s`, `5m`, `1h`) |
| `LOGIN_RATE_LIMIT_MAX_ATTEMPTS_PER_ACCOUNT` | `5` | Intentos fallidos por `(IP, email)` y ventana |
| `LOGIN_RATE_LIMIT_MAX_ATTEMPTS_PER_IP` | `30` | Intentos fallidos por IP y ventana |
| `LOGIN_RATE_LIMIT_TRUST_FORWARDED_HEADERS` | `false` | Lee `X-Forwarded-For`; activar solo detrás de un proxy de confianza |
| `LOGIN_RATE_LIMIT_STORE` | `memory` | `memory` o `redis` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / vacío | Solo se usan con `store=redis` |

Limitaciones conocidas:

- Con `store=memory` el estado es **por instancia**: con varias réplicas el límite no es global. Por
  eso existe `store=redis`; la dependencia (`spring-boot-starter-data-redis`) está siempre en el
  classpath, pero si no se activa no se abre ninguna conexión.
- Si Redis no responde, el limiter aplica *fail-open* (`WARN` en el log y el login sigue): una caída
  del caché no debe dejar a todo el mundo sin poder entrar.
- Si el backend está detrás de un proxy, `remoteAddr` es la IP del proxy. En ese caso hay que
  activar `LOGIN_RATE_LIMIT_TRUST_FORWARDED_HEADERS=true`; activarlo sin proxy permite falsificar
  el header y eludir el límite.

## Base de datos y migraciones (Flyway)

El esquema de PostgreSQL lo administra **Flyway**; Hibernate queda en
`spring.jpa.hibernate.ddl-auto: validate`, así que comprueba que las entidades coinciden con las
tablas pero **nunca** crea ni modifica el esquema.

- Las migraciones versionadas viven en `src/main/resources/db/migration`
  (`V1__initial_schema.sql`, `V2__...`, ...) y se aplican al arrancar la aplicación.
- `V1__initial_schema.sql` reproduce el esquema que creó Hibernate con `ddl-auto: update`: tablas
  `users` y `tasks`, identidad en las claves primarias, `UNIQUE (email)`, la clave foránea
  `tasks.user_id -> users.id` y la CHECK de `tasks.status`. Se conservan los nombres físicos de
  las constraints para que una base nueva y una existente queden idénticas.
- Cada migración aplicada queda registrada en `flyway_schema_history` (versión, checksum, fecha y
  resultado). Si un checksum cambia, el arranque falla en lugar de aplicar algo distinto.

### Base de datos nueva

Con la base vacía basta arrancar:

```powershell
.\gradlew.bat bootRun
```

Flyway aplica `V1` (y las siguientes en orden) y después Hibernate valida el esquema.

### Base de datos existente (adopción con baseline)

TaskFlow ya tenía datos antes de Flyway. **No se ejecuta `V1` sobre una base existente** y no se
borra nada: se marca la versión 1 como línea base y Flyway administra a partir de la siguiente
migración.

1. Haz una copia de seguridad (`pg_dump`).
2. Comprueba que el esquema existente coincide con `V1` (tablas, columnas, tipos, nulabilidad,
   PK, UNIQUE, CHECK y FK):

   ```sql
   SELECT table_name, column_name, data_type, is_nullable, column_default
   FROM information_schema.columns
   WHERE table_schema = 'public' AND table_name IN ('users', 'tasks')
   ORDER BY table_name, ordinal_position;

   SELECT c.relname AS tabla, co.conname, co.contype, pg_get_constraintdef(co.oid)
   FROM pg_constraint co
   JOIN pg_class c ON c.oid = co.conrelid
   WHERE c.relname IN ('users', 'tasks');
   ```

3. Arranca **una sola vez** con el baseline activado:

   ```powershell
   $env:FLYWAY_BASELINE_ON_MIGRATE = "true"
   .\gradlew.bat bootRun
   ```

   Se crea `flyway_schema_history` con la fila `1 | BASELINE` y **no se ejecuta ningún DDL**.

4. Elimina la variable y arranca de nuevo: Flyway valida y aplica las migraciones posteriores.

`baseline-on-migrate` está **desactivado por defecto** (`FLYWAY_BASELINE_ON_MIGRATE=false`) y no
debe quedar activo de forma permanente: es una decisión puntual y manual.

Garantías comprobadas por los tests de integración:

- Flyway solo hace baseline de esquemas **no vacíos**: una base vacía aplica `V1` normalmente y
  nunca queda marcada como "ya inicializada".
- Sin baseline, una base no vacía sin historial aborta el arranque **sin tocar nada**.
- Si el esquema no coincide con las entidades, Hibernate `validate` detiene el arranque y no
  repara el esquema por su cuenta.
- Limitación conocida: un esquema *parcialmente* creado (por ejemplo solo `users`) también se
  marca como línea base. Flyway no puede saber si el esquema está completo: la fila `1 | BASELINE`
  se escribe **antes** de que Hibernate valide y su `checksum` es `NULL`, de modo que no hay forma
  automática de detectar el desfase. Por eso el paso 2 es obligatorio.

#### Baseline aplicado sobre un esquema incompleto (recuperación)

Si el paso 3 se ejecutó sobre un esquema incompleto, el arranque falla en la validación de
Hibernate (`Schema-validation: missing table [tasks]`) **pero la fila de baseline ya está
registrada**:

```sql
SELECT installed_rank, version, description, type, success, checksum
FROM flyway_schema_history;
-- 1 | 1 | esquema previo a Flyway | BASELINE | t | (NULL)
```

Consecuencias:

- La fila **permanece** aunque Hibernate detenga el arranque: Flyway confirma el historial antes de
  que se inicialice JPA, así que no se revierte con el fallo.
- **Desactivar `baseline-on-migrate` después no lo soluciona**: la versión 1 ya figura en el
  historial, por lo que Flyway informa `Schema "public" is up to date. No migration necessary.` y
  **nunca** ejecuta `V1`; el error de validación se repite en cada arranque.
- El `checksum` de `BASELINE` es `NULL`, así que `validate` tampoco detecta que el esquema no
  coincide con `V1`.
- Tampoco sirve borrar solo `flyway_schema_history`: sobre un esquema no vacío Flyway aborta con
  `Found non-empty schema(s) "public" but no schema history table` y no modifica nada (en PostgreSQL
  el DDL de Flyway es transaccional, ni siquiera recrea el historial).

Recuperación segura (elegir **una** opción y no editar checksums ni la tabla de historial):

| Situación | Procedimiento |
| --- | --- |
| Solo existe la fila `BASELINE` (ninguna fila con `type` = `SQL`) y el esquema se puede restaurar desde `pg_dump` o recrear vacío | Restaurar la copia con `pg_restore` (o recrear la base vacía), ejecutar `DROP TABLE flyway_schema_history;` y arrancar **sin** baseline: `.\gradlew.bat bootRun` aplica `V1` completo. |
| Solo existe la fila `BASELINE` (ninguna fila con `type` = `SQL`) y no hay copia de seguridad, pero los datos existentes deben conservarse | Completar **a mano** los objetos que falten exactamente como `V1` (tablas, columnas, tipos, nulabilidad, PK, UNIQUE, CHECK y FK con los mismos nombres físicos) y **conservar** la fila `BASELINE`. El esquema queda equivalente a `V1` y las migraciones posteriores se aplican con normalidad. |
| Ya se aplicaron migraciones posteriores (`V2` en adelante) | **Restaurar la copia de seguridad** y repetir el baseline con el esquema ya verificado. No borrar el historial: se perdería el registro de migraciones aplicadas sobre datos reales. |

`DROP TABLE flyway_schema_history` **solo es seguro** en el primer caso: historial con únicamente la
fila `BASELINE` y un esquema que se va a restaurar o recrear por completo antes de volver a arrancar.
Si existen filas `SQL`, borrar la tabla obliga a Flyway a empezar de cero, `V1` falla con
`relation "users" already exists` y el arranque queda bloqueado.

> Comprobado empíricamente: baselinar un esquema con solo `users` deja `1 | BASELINE` registrada,
> Hibernate aborta con `missing table [tasks]`, y el arranque siguiente (con baseline desactivado)
> responde `Schema "public" is up to date. No migration necessary.` seguido del mismo error. Tras
> completar la tabla `tasks` con los objetos de `V1`, la aplicación arranca y valida el esquema.

### Compatibilidad con PostgreSQL 18

Flyway 11.7.2 (la versión que fija Spring Boot) declara soporte oficial hasta PostgreSQL 17 y en
cada arranque registra:

```
WARN ... PostgreSQL 18.1 is newer than this version of Flyway and support has not been tested.
The latest supported version of PostgreSQL is 17.
```

Es una advertencia, no un fallo: las migraciones y los tests de integración pasan contra PostgreSQL
18.1 (baseline, `V1`, validación de Hibernate y los escenarios de integración). Aun así, **una
prueba empírica satisfactoria no equivale a soporte oficial**: ante cualquier comportamiento
anómalo de las migraciones la referencia es la matriz de compatibilidad de Flyway, no esta
comprobación. Actualizar Flyway queda fuera de esta entrega.

### Ver el estado de las migraciones

```sql
SELECT installed_rank, version, description, type, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

| Variable | Por defecto | Uso |
| --- | --- | --- |
| `FLYWAY_ENABLED` | `true` | Desactiva Flyway (solo lo usa el test de contexto con H2) |
| `FLYWAY_BASELINE_ON_MIGRATE` | `false` | Adopción manual y puntual de una base existente |

## Manejo centralizado de errores

`GlobalExceptionHandler` (`@ControllerAdvice`) traduce todas las excepciones al mismo formato
`ErrorResponse` (`timestamp`, `status`, `message`, `path`, `details`). El frontend solo lee
`message` y el código HTTP, así que el contrato no cambia.

| Excepción | HTTP | Respuesta |
| --- | --- | --- |
| `MethodArgumentNotValidException` | 400 | `Error de validacion` + `details[campo]` |
| `ConstraintViolationException` | 400 | `Error de validacion` + `details[campo]` |
| `MethodArgumentTypeMismatchException` | 400 | Parámetro con valor no convertible (`?status=XX`) |
| `MissingServletRequestParameterException` | 400 | Falta un parámetro obligatorio |
| `HttpMessageNotReadableException` | 400 | JSON/enum no legible |
| `UserDataException`, `InvalidStatusTransitionException` | 400 | Reglas de negocio |
| `BadCredentialsException` | 401 | `usuario o contraseña incorrecto` |
| `DisabledException` | 401 | Mismo mensaje genérico: no revela el estado de la cuenta |
| `AuthenticationException` (resto) | 401 | Mensaje genérico |
| `AccessDeniedException` | 403 | `No tienes permisos para realizar esta acción` |
| `TaskAccessDeniedException` | 403 | Mensaje de la tarea |
| `UserNotFoundException`, `TaskNotFoundException` | 404 | Mensaje del recurso |
| `NoResourceFoundException` | 404 | `Recurso no encontrado` |
| `HttpRequestMethodNotSupportedException` | 405 | `Metodo HTTP no soportado` |
| `HttpMediaTypeNotAcceptableException` | 406 | Sin cuerpo: el cliente no acepta `application/json` |
| `HttpMediaTypeNotSupportedException` | 415 | `Tipo de contenido no soportado` (`Content-Type` no consumido) |
| `EmailAlreadyExistsException` | 409 | `email ya existente` |
| `InactiveUserException`, `InValidPasswordException` | 409 | Reglas de negocio |
| `DataIntegrityViolationException` | 409 / 400 | Traducida por SQLState (ver abajo) |
| `TooManyLoginAttemptsException` | 429 | `Retry-After` en segundos |
| `InternalAuthenticationServiceException` | 500 | Fallo del servidor durante la autenticación |
| `Exception` (resto) | 500 | `Ocurrió un error en el servidor` |

### Integridad de datos

`DataIntegrityViolationException` se traduce por `SQLState` para no convertir todos los errores de
persistencia en `409`:

| SQLState | Caso | HTTP |
| --- | --- | --- |
| `23505` | `UNIQUE (email)` | 409 `email ya existente` |
| `23505` | Otra restricción UNIQUE | 409 `Los datos enviados ya existen` |
| `23503` | Usuario con tareas (FK) | 409 `El usuario tiene tareas asociadas y no se puede eliminar` |
| `23502` | NOT NULL | 400 faltan datos obligatorios |
| `23514` | CHECK | 400 datos fuera de una restricción del modelo |
| `22001` | Longitud excedida | 400 dato demasiado largo |
| otro | Sin clasificar | 409 conflicto de integridad genérico |

`DELETE /users/{id}` es transaccional (`@Transactional` + `flush`): la violación de la clave
foránea se lanza dentro de la transacción, se revierte por completo y el cliente recibe `409` sin
que se borre ni modifique nada.

### Qué no se expone nunca

- No se devuelven mensajes de PostgreSQL/Hibernate, sentencias SQL, nombres de constraints,
  stack traces ni rutas de clases internas.
- El detalle completo (con traza) se registra en el log del servidor con `ERROR`.
- `details` solo contiene `nombre de campo -> mensaje de validación`.

## Requisitos

- Java 21
- PostgreSQL
- Gradle Wrapper incluido en el proyecto.

## Configuración

La aplicación utiliza variables de entorno para configurar la conexión a la base de datos y la autenticación.

Variables necesarias:

- `DB_USERNAME`: usuario de PostgreSQL.
- `DB_PASSWORD`: contraseña de PostgreSQL.
- `JWT_SECRET`: secreto utilizado para firmar los tokens JWT.

La configuración de la base de datos se encuentra en `application.yaml`.

## Ejecución local

Configura las variables de entorno y asegúrate de que PostgreSQL esté disponible en la dirección definida en la configuración.

En Windows, ejecuta:

```powershell
.\gradlew.bat bootRun
```

## Pruebas

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

### Tests de integración con PostgreSQL (Flyway)

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

## Integración continua

El proyecto utiliza Jenkins para ejecutar la compilación y las pruebas automatizadas mediante el archivo `Jenkinsfile`.

El pipeline obtiene el código desde GitHub, ejecuta las pruebas con Gradle y registra sus resultados.

## Estado del proyecto

Proyecto personal de portafolio orientado al desarrollo backend con Kotlin y Spring Boot.
