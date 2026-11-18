# TaskFlow API

**Backend REST de TaskFlow**: gestión de tareas y usuarios con autenticación JWT, autorización por
roles y esquema versionado con Flyway.
**Kotlin · Spring Boot 3.5 · PostgreSQL**

| Recurso | Enlace |
| --- | --- |
| **API desplegada** (Render) | https://taskflow-api-ycus.onrender.com |
| **Aplicación web** (Vercel) | https://taskflow-frontend-ashen.vercel.app |
| **Repositorio del frontend** | https://github.com/NAHUM099/taskflow-frontend |
| **Capturas de la aplicación** | [taskflow-frontend › Capturas](https://github.com/NAHUM099/taskflow-frontend#capturas) |

> La API está en el plan gratuito de Render, que suspende la instancia por inactividad: la primera
> petición tras un periodo sin tráfico puede tardar hasta un minuto en responder.

---

## Qué resuelve

Gestionar tareas en equipo sin montar una plataforma grande: cada persona ve y mueve sus propias
tareas, los moderadores pueden crear tareas para cualquiera y solo un administrador gestiona las
cuentas. TaskFlow cubre ese flujo completo en una API pequeña pero con las decisiones que importan
en un entorno real: autenticación sin estado, permisos por rol, contrato de errores uniforme,
esquema versionado y límite de intentos de login.

Este repositorio es el backend; la interfaz web vive en
[taskflow-frontend](https://github.com/NAHUM099/taskflow-frontend).

## Funcionalidades

- **Autenticación JWT** stateless (`POST /auth/login` es la única ruta pública) y contraseñas
  cifradas con BCrypt.
- **Autorización por roles** `USER` / `MODERATOR` / `ADMIN`, con `401` y `403` correctamente
  separados. Un `USER` solo accede a sus propias tareas.
- **Tareas**: listado paginado, búsqueda por título, filtro por estado y por usuario, resumen de
  indicadores (total, pendientes, en progreso, completadas, canceladas), alta de tareas y cambio de
  estado con transiciones validadas.
- **Usuarios** (solo `ADMIN`): listado paginado con búsqueda, alta, edición, cambio de contraseña,
  activación/desactivación y borrado protegido por la clave foránea de sus tareas.
- **Rate limiting del login** por `(IP, email)` y por `IP`, con almacén en memoria o Redis.
- **Contrato de errores único** (`ErrorResponse`) para validación, seguridad, conflictos e
  integridad de datos, sin filtrar detalles internos.
- **Migraciones Flyway** versionadas; Hibernate solo valida el esquema (`ddl-auto: validate`).

## Stack

| Pieza | Tecnología |
| --- | --- |
| Lenguaje / runtime | Kotlin 1.9.25 · JDK 21 |
| Framework | Spring Boot 3.5.14 (Web MVC, Security, Data JPA, Validation) |
| Persistencia | PostgreSQL · Hibernate (`validate`) · Flyway 11.7.2 |
| Autenticación | JWT con JJWT 0.12.7 · BCrypt |
| Rate limiting | `spring-boot-starter-data-redis` (opcional, `store=redis`) |
| Build | Gradle 8.14.4 (wrapper incluido) |
| Pruebas | JUnit 5 · Spring Boot Test · Spring Security Test · H2 (solo test de contexto) |

## Arquitectura

```
Cliente (taskflow-frontend)
        │  HTTPS + JWT (Authorization: Bearer ...)
        ▼
JwtAuthenticationFilter ──► SecurityFilterChain (roles)
        ▼
Controller  ──►  Service  ──►  Repository  ──►  PostgreSQL
   DTOs         reglas         Spring Data      esquema Flyway
                de negocio      JPA
```

- **`controller`**: expone los endpoints y traduce peticiones a DTOs; sin lógica de negocio.
- **`service`**: reglas de negocio, control de acceso a nivel de recurso y transacciones.
- **`repository`**: acceso a datos con Spring Data JPA (paginación y búsquedas derivadas).
- **`security`**: filtro JWT, provisión de usuarios, rate limiting y manejo de `401`/`403`.
- **`exception`**: `GlobalExceptionHandler` convierte cualquier excepción en `ErrorResponse`.
- **`db/migration`**: migraciones Flyway, única fuente de verdad del esquema.

La arquitectura de la aplicación completa está descrita en
[docs/architecture.md](https://github.com/NAHUM099/taskflow-frontend/blob/main/docs/architecture.md)
del repositorio del frontend.

## Endpoints

| Método | Ruta | Acceso |
| --- | --- | --- |
| `POST` | `/auth/login` | Público |
| `GET` | `/users` · `/users/{id}` · `/users/{id}/tasks` | `ADMIN` |
| `POST` | `/users` | `ADMIN` |
| `PATCH` | `/users/{id}` · `/users/{id}/status` · `/users/{id}/password` | `ADMIN` |
| `PATCH` | `/users/me/password` | `ADMIN` (hereda la regla `/users/**`) |
| `DELETE` | `/users/{id}` | `ADMIN` |
| `GET` | `/tasks` (`page`, `size`, `sort`, `status`, `userId`, `search`) | Autenticado (un `USER` solo ve las suyas) |
| `GET` | `/tasks/summary` | Autenticado (un `USER` solo ve las suyas) |
| `PATCH` | `/tasks/{id}/status` | Autenticado (un `USER` solo las suyas) |
| `POST` | `/tasks` | `ADMIN`, `MODERATOR` |

Filtros, paginación y forma de las respuestas:
[api-contracts.md](https://github.com/NAHUM099/taskflow-frontend/blob/main/docs/api-contracts.md).

## Ejecución local

### Requisitos

| Herramienta | Versión | Comprobación |
| --- | --- | --- |
| JDK | **21** (el `build.gradle.kts` fija la toolchain en 21) | `java -version` |
| PostgreSQL | 14 o superior (probado con 18) | `psql --version` |
| Gradle | No hace falta instalarlo: usa el wrapper incluido | `.\gradlew.bat -v` |

### 1. Base de datos

Crea la base de datos. El proyecto apunta al puerto **5434** por defecto para no chocar con la
instalación estándar (5432):

```sql
CREATE DATABASE taskflow;
```

Si tu PostgreSQL escucha en otro puerto, define `SPRING_DATASOURCE_URL` (ver
[Variables de entorno](#variables-de-entorno)) en lugar de tocar el código.

### 2. Variables de entorno

`DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET` son obligatorias: la aplicación no arranca sin ellas.
La plantilla con todas las variables y sus valores por defecto es [`.env.example`](.env.example).

> **Spring Boot no lee archivos `.env`.** Exporta las variables en la terminal, configúralas en el
> IDE o defínelas en la plataforma de despliegue.

```powershell
# Windows (PowerShell)
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "tu-contrasena"
$env:JWT_SECRET  = "cadena-larga-y-aleatoria-de-al-menos-32-caracteres"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5434/taskflow"
```

```bash
# Linux / macOS
export DB_USERNAME="postgres"
export DB_PASSWORD="tu-contrasena"
export JWT_SECRET="cadena-larga-y-aleatoria-de-al-menos-32-caracteres"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5434/taskflow"
```

### 3. Arrancar la API

```powershell
.\gradlew.bat bootRun      # Windows
```

```bash
./gradlew bootRun          # Linux / macOS
```

Flyway aplica las migraciones al arrancar y la API queda escuchando en `http://localhost:8080`
(modificable con `PORT`).

Comprobación rápida:

```bash
curl -i http://localhost:8080/tasks
# HTTP/1.1 401 Unauthorized -> el backend responde y la seguridad está activa
```

### 4. Primer usuario

Con una base de datos recién migrada **no existe ningún usuario** y el registro público está
deshabilitado (`POST /users` exige `ROLE_ADMIN`). El primer administrador se crea directamente en
PostgreSQL; el procedimiento está en **[docs/cuenta-demo.md](docs/cuenta-demo.md)** (Opción B).
A partir de ahí, el resto de cuentas se crean con `POST /users`, que asigna siempre `ROLE_USER`.

### 5. Compilar el JAR

```powershell
.\gradlew.bat clean bootJar      # -> build/libs/taskflow-api-0.0.1-SNAPSHOT.jar
```

```bash
./gradlew clean bootJar
```

## Variables de entorno

Todas son opcionales salvo las marcadas como obligatorias. Los valores por defecto están en
`src/main/resources/application.yaml`.

| Variable | Por defecto | Descripción |
| --- | --- | --- |
| `DB_USERNAME` / `DB_PASSWORD` | — (**obligatorias**) | Credenciales de PostgreSQL |
| `JWT_SECRET` | — (**obligatoria**) | Clave de firma HS256 (32 caracteres mínimo) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5434/taskflow` | URL JDBC completa |
| `PORT` | `8080` | Puerto HTTP |
| `FLYWAY_ENABLED` | `true` | Desactiva las migraciones (solo test de contexto) |
| `FLYWAY_BASELINE_ON_MIGRATE` | `false` | Adopción de una base existente (ver [docs/base-de-datos.md](docs/base-de-datos.md)) |
| `LOGIN_RATE_LIMIT_ENABLED` | `true` | Activa el límite de intentos de login |
| `LOGIN_RATE_LIMIT_WINDOW` | `5m` | Ventana del límite (`30s`, `5m`, `1h`) |
| `LOGIN_RATE_LIMIT_MAX_ATTEMPTS_PER_ACCOUNT` | `5` | Intentos fallidos por `(IP, email)` |
| `LOGIN_RATE_LIMIT_MAX_ATTEMPTS_PER_IP` | `30` | Intentos fallidos por IP |
| `LOGIN_RATE_LIMIT_TRUST_FORWARDED_HEADERS` | `false` | Lee `X-Forwarded-For` (solo detrás de un proxy de confianza) |
| `LOGIN_RATE_LIMIT_STORE` | `memory` | `memory` (por instancia) o `redis` (compartido) |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / vacío | Solo con `store=redis` |
| `TASKFLOW_PG_IT_URL` | sin definir | Solo tests: base de mantenimiento para los tests de Flyway |

## Pruebas

```powershell
.\gradlew.bat clean test      # Windows
```

```bash
./gradlew clean test          # Linux / macOS
```

**Resultado actual: 92 tests identificados, 82 ejecutados, 10 omitidos y 0 fallos** (17 clases de
prueba). Los 10 restantes son los tests de integración de Flyway y se omiten por no disponer de
`TASKFLOW_PG_IT_URL`. Los 82 ejecutados no necesitan PostgreSQL ni Redis: el test de contexto usa H2
en memoria y el rate limiting se prueba con el almacén en memoria y con un `StringRedisTemplate`
simulado.

Los tests de integración de Flyway (crean y borran bases desechables `taskflow_it_*`) se ejecutan solo
si se indica una base de mantenimiento:

```powershell
$env:TASKFLOW_PG_IT_URL = "jdbc:postgresql://localhost:5434/postgres"
.\gradlew.bat cleanTest test --tests "com.taskflow.api.migration.*"
```

Estrategia y escenarios cubiertos: [docs/testing.md](docs/testing.md).

## Documentación

| Documento | Contenido |
| --- | --- |
| [docs/seguridad.md](docs/seguridad.md) | Reglas de acceso, alta de usuarios y rate limiting del login |
| [docs/base-de-datos.md](docs/base-de-datos.md) | Esquema, migraciones Flyway y adopción de bases existentes |
| [docs/errores.md](docs/errores.md) | Traducción de excepciones a `ErrorResponse` |
| [docs/testing.md](docs/testing.md) | Estrategia de pruebas y tests de integración |
| [docs/cuenta-demo.md](docs/cuenta-demo.md) | Cómo habilitar una cuenta de demostración con rol `USER` |

La documentación del frontend (contratos de API, arquitectura del cliente, sistema de diseño) está en
[taskflow-frontend › docs](https://github.com/NAHUM099/taskflow-frontend/tree/main/docs).

## Integración continua

El repositorio incluye un `Jenkinsfile` que compila el proyecto y ejecuta la suite de pruebas en cada
cambio.

## Estado del proyecto

Backend funcional y estable (MVP completo). Limitaciones conocidas:

- No se genera documentación OpenAPI/Swagger: los contratos están en
  [api-contracts.md](https://github.com/NAHUM099/taskflow-frontend/blob/main/docs/api-contracts.md).
- Los tests de integración de Flyway requieren PostgreSQL real y se omiten por defecto.
- No se mide cobertura con una herramienta dedicada en el backend.
- Existe un `Dockerfile` para construir y desplegar la imagen de la API; no hay `docker-compose.yml` (el
  aprovisionamiento local sigue siendo manual).
