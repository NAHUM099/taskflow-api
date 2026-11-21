# Seguridad

Reglas de acceso, alta de usuarios y protección del login. Resumen del proyecto en el [README](../README.md).

## Reglas de acceso

- `POST /auth/login` es la única ruta pública.
- `/users/**`, incluido `POST /users`, requiere `ROLE_ADMIN`: **el registro público está deshabilitado**.
- `POST /tasks` requiere `ROLE_ADMIN` o `ROLE_MODERATOR`.
- Cualquier otra ruta requiere un JWT válido.
- Las respuestas de seguridad respetan el formato `ErrorResponse`: `401` cuando la petición no
  lleva autenticación válida (token ausente, malformado, expirado o de usuario inactivo) y `403`
  cuando el usuario está autenticado pero no tiene permisos.

## CORS

Los orígenes del frontend se configuran con `CORS_ALLOWED_ORIGINS` (lista separada por comas). No se
admite `*`: un comodín permitiría que cualquier sitio usara el JWT del usuario desde el navegador. La
configuración recorta espacios y barras finales, registra CORS en la cadena de seguridad y expone
`Retry-After` para que el cliente pueda leer el `429` del rate limiting.

En local el valor por defecto es `http://localhost:3000`; en Render debe definirse el origen real del
frontend desplegado.

## Alta de usuarios (`POST /users`)

Solo un `ADMIN` autenticado puede crear cuentas. El cuerpo (`CreateUserRequest`) no incluye el
campo `role`: si el cliente envía `role` en el JSON se ignora y el backend asigna siempre
`ROLE_USER` (`UserMapper.toEntity`). La promoción a otro rol se hace fuera de la API (base de
datos), nunca manipulando la petición. Se mantienen las validaciones (`@NotBlank`, `@Email`,
`@Size(min = 6)`) y la unicidad del correo (`409 Conflict`).

## Rate limiting del login

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
