package com.taskflow.api.exception

import com.taskflow.api.exception.classException.EmailAlreadyExistsException
import com.taskflow.api.exception.classException.InValidPasswordException
import com.taskflow.api.exception.classException.InactiveUserException
import com.taskflow.api.exception.classException.InvalidStatusTransitionException
import com.taskflow.api.exception.classException.TaskAccessDeniedException
import com.taskflow.api.exception.classException.TaskNotFoundException
import com.taskflow.api.exception.classException.TooManyLoginAttemptsException
import com.taskflow.api.exception.classException.UserDataException
import com.taskflow.api.exception.classException.UserNotFoundException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.InternalAuthenticationServiceException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.sql.SQLException
import java.time.LocalDateTime


@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * Construye la respuesta con el formato `ErrorResponse` que ya consume el
     * frontend (`status` y `message`). `details` se reserva para errores por
     * campo y nunca incluye clases, paquetes ni detalles internos.
     */
    private fun errorResponse(
        request: HttpServletRequest,
        status: HttpStatus,
        message: String,
        details: Map<String, String>? = null
    ) : ResponseEntity<ErrorResponse> {

        val response = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = status.value(),
            message = message,
            path = request.requestURI,
            details = details
        )

        return ResponseEntity(response, status)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun exceptionHandler(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        val errors = mutableMapOf<String, String>()

        ex.bindingResult.fieldErrors.forEach { error ->

            errors[error.field] =
                error.defaultMessage ?: "Error de validacion"
        }

        val response = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            message = "Error de validacion",
            path = request.requestURI,
            details = errors
        )

        return ResponseEntity(
            response,
            HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun exceptionEmail (
        ex : EmailAlreadyExistsException,
        request : HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        val response = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.CONFLICT.value(),
            message = ex.message ?: "Email ya existente",
            path = request.requestURI
        )

        return ResponseEntity (
            response, HttpStatus.CONFLICT
        )
    }

    @ExceptionHandler (UserNotFoundException::class)
    fun exceptionUser (
        ex : UserNotFoundException,
        request : HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {


        val response = ErrorResponse (
            timestamp = LocalDateTime.now(),
            status = HttpStatus.NOT_FOUND.value(),
            message = ex.message?: "usario no existente",
            path = request.requestURI
        )
        return ResponseEntity(
            response, HttpStatus.NOT_FOUND
        )
    }

    @ExceptionHandler (UserDataException::class)
    fun badRequest(
        ex: UserDataException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        val response = ErrorResponse (
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            message = ex.message ?: "nombre invalido",
            path = request.requestURI
        )

        return ResponseEntity(
            response, HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(TaskNotFoundException::class)
    fun taskNotFound(
        ex: TaskNotFoundException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {
        val  response = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.NOT_FOUND.value(),
            message = ex.message ?: "tarea no encotrada",
            path = request.requestURI
        )

        return ResponseEntity(
            response, HttpStatus.NOT_FOUND
        )
    }

    @ExceptionHandler(TaskAccessDeniedException::class)
    fun taskAccessDenied(
        ex: TaskAccessDeniedException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.FORBIDDEN.value(),
            message = ex.message ?: "No tienes permisos sobre esta tarea",
            path = request.requestURI
        )

        return ResponseEntity(
            response, HttpStatus.FORBIDDEN
        )
    }

    @ExceptionHandler(InvalidStatusTransitionException::class)
    fun invalidStatusTrasition(
        ex : InvalidStatusTransitionException,
        request : HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        val response = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            message = ex.message ?: "estatus invalido",
            path = request.requestURI
        )

        return ResponseEntity(
            response, HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun ExceptionConvertToEnum (
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        val response = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            message = ex.message ?: "status invalidos",
            path = request.requestURI
        )

        return ResponseEntity(
            response, HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException::class)
    fun badCredentialsException(
        ex: org.springframework.security.authentication.BadCredentialsException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        val response = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.UNAUTHORIZED.value(),
            message = "usuario o contraseña incorrecto",
            path = request.requestURI
        )

        return ResponseEntity(
            response, HttpStatus.UNAUTHORIZED
        )
    }

    @ExceptionHandler(DisabledException::class)
    fun disabledException(
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        // Caso conocido del proceso de login: cuenta desactivada. Se responde
        // 401 con el mismo mensaje genérico que unas credenciales incorrectas
        // para no revelar el estado de la cuenta.
        val response = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.UNAUTHORIZED.value(),
            message = "usuario o contraseña incorrecto",
            path = request.requestURI
        )

        return ResponseEntity(
            response, HttpStatus.UNAUTHORIZED
        )
    }

    @ExceptionHandler(InactiveUserException::class)
    fun inactiveException (
        ex: InactiveUserException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        val response = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.CONFLICT.value(),
            message = "Usuario inactivo",
            path = request.requestURI
        )
        return ResponseEntity(
            response, HttpStatus.CONFLICT
        )
    }

    @ExceptionHandler(InValidPasswordException::class)
    fun invalidPassword(
        ex: InValidPasswordException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        val response = ErrorResponse (
            timestamp = LocalDateTime.now(),
            status = HttpStatus.CONFLICT.value(),
            message = "contraseña incorrecta",
            path = request.requestURI
        )
        return ResponseEntity(response, HttpStatus.CONFLICT)
    }

    @ExceptionHandler(TooManyLoginAttemptsException::class)
    fun tooManyLoginAttempts(
        ex: TooManyLoginAttemptsException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        // Rate limiting del login: se informa cuándo reintentar sin revelar si
        // el correo existe ni el estado de la cuenta.
        val response = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.TOO_MANY_REQUESTS.value(),
            message = "Demasiados intentos de inicio de sesión. Inténtalo de nuevo en ${ex.retryAfterSeconds} segundos.",
            path = request.requestURI
        )

        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, ex.retryAfterSeconds.toString())
            .body(response)
    }

    // --- Validación de Jakarta (parámetros de la petición) -------------------

    @ExceptionHandler(ConstraintViolationException::class)
    fun constraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        // Bean Validation sobre parámetros (@Validated). Se publica el nombre
        // del campo y el mensaje de la restricción, nada más.
        val errors = ex.constraintViolations.associate { violation ->
            val field = violation.propertyPath.lastOrNull()?.name ?: violation.propertyPath.toString()
            field to (violation.message ?: "Error de validacion")
        }

        return errorResponse(request, HttpStatus.BAD_REQUEST, "Error de validacion", errors)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun argumentTypeMismatch(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        // ?status=INVENTADO o ?userId=abc: el valor no convertible es un error
        // del cliente, no del servidor.
        return errorResponse(
            request,
            HttpStatus.BAD_REQUEST,
            "Error de validacion",
            mapOf(ex.name to "valor no valido")
        )
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParameter(
        ex: MissingServletRequestParameterException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        return errorResponse(
            request,
            HttpStatus.BAD_REQUEST,
            "Error de validacion",
            mapOf(ex.parameterName to "parametro requerido")
        )
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotSupported(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        return errorResponse(request, HttpStatus.METHOD_NOT_ALLOWED, "Metodo HTTP no soportado")
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun mediaTypeNotSupported(
        ex: HttpMediaTypeNotSupportedException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        // 415: el Content-Type enviado no lo consume ningún endpoint. El mensaje
        // de Spring incluye el tipo recibido y los tipos aceptados: se registra
        // en el servidor y no se devuelve al cliente.
        logger.warn(
            "Content-Type no soportado en {} {}: {}",
            request.method,
            request.requestURI,
            ex.message
        )

        return errorResponse(
            request,
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Tipo de contenido no soportado"
        )
    }

    /**
     * 406: el cliente no acepta `application/json`, así que intentar escribir un
     * `ErrorResponse` volvería a lanzar la misma excepción (500 y, tras el
     * dispatch a `/error`, un 401 engañoso). Se responde sin cuerpo.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun mediaTypeNotAcceptable(
        ex: HttpMediaTypeNotAcceptableException,
        request: HttpServletRequest
    ) : ResponseEntity<Void> {

        logger.warn(
            "Formato de respuesta no soportado en {} {}: {}",
            request.method,
            request.requestURI,
            ex.message
        )

        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build()
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun resourceNotFound(
        ex: NoResourceFoundException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        return errorResponse(request, HttpStatus.NOT_FOUND, "Recurso no encontrado")
    }

    // --- Integridad de datos (restricciones de PostgreSQL) -------------------

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun dataIntegrityViolation(
        ex: DataIntegrityViolationException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        // El mensaje de PostgreSQL/Hibernate puede incluir la sentencia SQL, el
        // valor duplicado o el nombre de la constraint: se registra en el
        // servidor y NUNCA se devuelve al cliente.
        val integrityError = classifyIntegrityViolation(ex)

        logger.error(
            "Violacion de integridad de datos [{}] en {} {}",
            integrityError.logCode,
            request.method,
            request.requestURI,
            ex
        )

        return errorResponse(request, integrityError.status, integrityError.message)
    }

    /**
     * Traduce el SQLState de PostgreSQL a una respuesta concreta. Solo se
     * declaran como conflicto (409) las violaciones de unicidad y de clave
     * foránea; el resto de fallos de integridad son datos inválidos (400).
     */
    private fun classifyIntegrityViolation(ex: DataIntegrityViolationException) : IntegrityError {

        val detail = ex.mostSpecificCause.message.orEmpty().lowercase()

        return when (sqlStateOf(ex)) {
            // unique_violation
            "23505" -> when {
                detail.contains(USERS_EMAIL_CONSTRAINT) || detail.contains("(email)") ->
                    IntegrityError(HttpStatus.CONFLICT, "email ya existente", "unique_users_email")
                else ->
                    IntegrityError(HttpStatus.CONFLICT, "Los datos enviados ya existen", "unique_generic")
            }
            // foreign_key_violation
            "23503" -> when {
                detail.contains(TASKS_USER_FK) || detail.contains("tareas asociadas") ->
                    IntegrityError(
                        HttpStatus.CONFLICT,
                        "El usuario tiene tareas asociadas y no se puede eliminar",
                        "fk_tasks_user"
                    )
                else ->
                    IntegrityError(
                        HttpStatus.CONFLICT,
                        "La operacion entra en conflicto con datos relacionados",
                        "fk_generic"
                    )
            }
            // not_null_violation
            "23502" -> IntegrityError(
                HttpStatus.BAD_REQUEST,
                "Faltan datos obligatorios para completar la operacion",
                "not_null"
            )
            // check_violation
            "23514" -> IntegrityError(
                HttpStatus.BAD_REQUEST,
                "Los datos enviados no cumplen una restriccion del modelo",
                "check"
            )
            // string_data_right_truncation
            "22001" -> IntegrityError(
                HttpStatus.BAD_REQUEST,
                "Uno de los datos enviados supera la longitud permitida",
                "too_long"
            )
            else -> IntegrityError(
                HttpStatus.CONFLICT,
                "La operacion no se pudo completar por un conflicto de integridad de datos",
                "integrity_generic"
            )
        }
    }

    /**
     * Recorre la cadena de causas hasta encontrar la `SQLException` original:
     * así no se depende del driver concreto ni de sus clases internas.
     */
    private fun sqlStateOf(ex: Throwable) : String? {

        var current: Throwable? = ex

        while (current != null) {
            if (current is SQLException && !current.sqlState.isNullOrBlank()) {
                return current.sqlState
            }
            current = current.cause
        }

        return null
    }

    private data class IntegrityError(
        val status: HttpStatus,
        val message: String,
        val logCode: String
    )

    // --- Excepciones de seguridad fuera de la cadena de filtros ---------------

    @ExceptionHandler(AccessDeniedException::class)
    fun accessDenied(
        ex: AccessDeniedException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        // Mismo mensaje que SecurityErrorHandler: la respuesta no puede
        // depender de si el 403 lo lanza el filtro o el controlador.
        return errorResponse(
            request,
            HttpStatus.FORBIDDEN,
            "No tienes permisos para realizar esta acción"
        )
    }

    @ExceptionHandler(AuthenticationException::class)
    fun authentication(
        ex: AuthenticationException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        // Fallos de autenticación que no tienen manejador propio (cuenta
        // bloqueada, credenciales expiradas...): 401 y mensaje genérico.
        return errorResponse(request, HttpStatus.UNAUTHORIZED, "usuario o contraseña incorrecto")
    }

    @ExceptionHandler(InternalAuthenticationServiceException::class)
    fun authenticationServiceFailure(
        ex: InternalAuthenticationServiceException,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        // El proveedor falló por un problema del servidor (por ejemplo la base
        // de datos), no por credenciales incorrectas: 500 y log completo.
        logger.error(
            "Fallo interno durante la autenticacion en {} {}",
            request.method,
            request.requestURI,
            ex
        )

        return errorResponse(
            request,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Ocurrió un error en el servidor"
        )
    }

    // --- Último recurso -------------------------------------------------------

    @ExceptionHandler(Exception::class)
    fun unexpectedError(
        ex: Exception,
        request: HttpServletRequest
    ) : ResponseEntity<ErrorResponse> {

        // Red de seguridad: la traza completa queda en el servidor y el cliente
        // recibe un 500 uniforme, sin mensajes internos ni stack traces.
        logger.error("Error no controlado en {} {}", request.method, request.requestURI, ex)

        return errorResponse(
            request,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Ocurrió un error en el servidor"
        )
    }

    private companion object {
        // Nombres físicos de las constraints del esquema (ver V1__initial_schema.sql).
        const val USERS_EMAIL_CONSTRAINT = "uk6dotkott2kjsp8vw4d0m25fb7"
        const val TASKS_USER_FK = "fk6s1ob9k4ihi75xbxe2w0ylsdh"
    }

}