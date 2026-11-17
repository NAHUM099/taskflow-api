package com.taskflow.api.exception

import com.taskflow.api.dto.user.CreateUserRequest
import com.taskflow.api.enums.TaskStatus
import com.taskflow.api.exception.classException.EmailAlreadyExistsException
import com.taskflow.api.security.JwtService
import com.taskflow.api.security.TaskFlowUserDetailsService
import jakarta.validation.Valid
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.DisabledException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.SQLException

/**
 * Verifica el contrato HTTP del manejo centralizado de excepciones a través de
 * Spring MVC: formato `ErrorResponse`, códigos de estado y ausencia de detalles
 * internos (SQL, clases, mensajes de PostgreSQL).
 *
 * Los filtros de seguridad se desactivan para aislar el `@ControllerAdvice`.
 */
@WebMvcTest(controllers = [ExceptionHandlingEndpointTest.ErrorProbeController::class])
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.context.annotation.Import(ExceptionHandlingEndpointTest.ErrorProbeController::class)
class ExceptionHandlingEndpointTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    // El filtro JWT forma parte del slice web: se sustituye para no necesitar
    // usuarios ni secretos reales.
    @MockitoBean
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var userDetailsService: TaskFlowUserDetailsService

    @Test
    fun `un DTO invalido devuelve 400 con los errores por campo`() {
        mockMvc.perform(
            post("/probe/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"","email":"no-es-email","password":"123"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Error de validacion"))
            .andExpect(jsonPath("$.path").value("/probe/users"))
            .andExpect(jsonPath("$.details.name").value("nombre requerido"))
            .andExpect(jsonPath("$.details.email").value("correo invalido"))
            .andExpect(
                jsonPath("$.details.password").value("la contraseña debe tener minimo 6 caracteres")
            )
            .andExpect(content().string(not(containsString("com.taskflow"))))
    }

    @Test
    fun `un parametro con enum invalido devuelve 400 y no 500`() {
        mockMvc.perform(get("/probe/tasks").param("status", "INVENTADO"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.details.status").value("valor no valido"))
    }

    @Test
    fun `un parametro obligatorio ausente devuelve 400`() {
        mockMvc.perform(get("/probe/required"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.details.q").value("parametro requerido"))
    }

    @Test
    fun `una excepcion inesperada devuelve 500 sin filtrar informacion interna`() {
        mockMvc.perform(get("/probe/boom"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.message").value("Ocurrió un error en el servidor"))
            .andExpect(content().string(not(containsString("secreta"))))
            .andExpect(content().string(not(containsString("IllegalStateException"))))
    }

    @Test
    fun `un email duplicado detectado por el servicio devuelve 409`() {
        mockMvc.perform(get("/probe/duplicate"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("email ya existente"))
    }

    @Test
    fun `una violacion UNIQUE de PostgreSQL devuelve 409 sin detalles de SQL`() {
        mockMvc.perform(get("/probe/integrity/email"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("email ya existente"))
            .andExpect(content().string(not(containsString("uk6dotkott2kjsp8vw4d0m25fb7"))))
            .andExpect(content().string(not(containsString("duplicate key"))))
    }

    @Test
    fun `una violacion de clave foranea devuelve 409 controlado`() {
        mockMvc.perform(get("/probe/integrity/fk"))
            .andExpect(status().isConflict)
            .andExpect(
                jsonPath("$.message")
                    .value("El usuario tiene tareas asociadas y no se puede eliminar")
            )
            .andExpect(content().string(not(containsString("fk6s1ob9k4ihi75xbxe2w0ylsdh"))))
    }

    @Test
    fun `AccessDeniedException lanzada en el controlador responde 403`() {
        mockMvc.perform(delete("/probe/users/7"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.message").value("No tienes permisos para realizar esta acción"))
    }

    @Test
    fun `una cuenta desactivada durante el login responde 401 sin revelar el estado`() {
        mockMvc.perform(post("/probe/login-disabled"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("usuario o contraseña incorrecto"))
            .andExpect(content().string(not(containsString("User is disabled"))))
    }

    /**
     * Controlador mínimo que reproduce cada escenario sin tocar la lógica real.
     */
    @RestController
    class ErrorProbeController {

        @PostMapping("/probe/users")
        fun create(@Valid @RequestBody request: CreateUserRequest): String = "ok"

        @GetMapping("/probe/tasks")
        fun tasks(@RequestParam(required = false) status: TaskStatus?): String = "ok"

        @GetMapping("/probe/required")
        fun required(@RequestParam q: String): String = "ok"

        @GetMapping("/probe/boom")
        fun boom(): String = throw IllegalStateException("fallo interno con password=secreta")

        @GetMapping("/probe/duplicate")
        fun duplicate(): String = throw EmailAlreadyExistsException("email ya existente")

        @GetMapping("/probe/integrity/email")
        fun integrityEmail(): String = throw DataIntegrityViolationException(
            "could not execute statement",
            SQLException(
                """ERROR: duplicate key value violates unique constraint "uk6dotkott2kjsp8vw4d0m25fb7"""",
                "23505"
            )
        )

        @GetMapping("/probe/integrity/fk")
        fun integrityForeignKey(): String = throw DataIntegrityViolationException(
            "could not execute statement",
            SQLException(
                """ERROR: delete on table "users" violates foreign key """ +
                    """constraint "fk6s1ob9k4ihi75xbxe2w0ylsdh"""",
                "23503"
            )
        )

        @DeleteMapping("/probe/users/{id}")
        fun deleteUser(@PathVariable id: Long): String =
            throw AccessDeniedException("no permitido")

        @PostMapping("/probe/login-disabled")
        fun loginDisabled(): String = throw DisabledException("User is disabled")
    }
}
