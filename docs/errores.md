# Contrato de errores

Traducción centralizada de excepciones a respuestas `ErrorResponse`. Resumen del proyecto en el [README](../README.md).

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

## Integridad de datos

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

## Qué no se expone nunca

- No se devuelven mensajes de PostgreSQL/Hibernate, sentencias SQL, nombres de constraints,
  stack traces ni rutas de clases internas.
- El detalle completo (con traza) se registra en el log del servidor con `ERROR`.
- `details` solo contiene `nombre de campo -> mensaje de validación`.
