package com.taskflow.api.exception

import com.taskflow.api.exception.classException.EmailAlreadyExistsException
import com.taskflow.api.exception.classException.InValidPasswordException
import com.taskflow.api.exception.classException.InactiveUserException
import com.taskflow.api.exception.classException.InvalidStatusTransitionException
import com.taskflow.api.exception.classException.TaskNotFoundException
import com.taskflow.api.exception.classException.UserDataException
import com.taskflow.api.exception.classException.UserNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.time.LocalDateTime


@ControllerAdvice
class GlobalExceptionHandler {

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

}