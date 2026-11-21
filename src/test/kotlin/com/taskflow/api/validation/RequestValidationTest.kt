package com.taskflow.api.validation

import com.taskflow.api.dto.task.CreateTaskRequest
import com.taskflow.api.dto.user.CreateUserRequest
import com.taskflow.api.dto.user.UpdateUserRequest
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RequestValidationTest {

    private lateinit var validator: Validator

    @BeforeAll
    fun setUp() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    @Test
    fun `crear usuario rechaza un email vacio`() {
        assertViolation(
            CreateUserRequest("Ana Torres", "   ", "secreta123"),
            "email"
        )
    }

    @Test
    fun `crear usuario respeta los limites de varchar 255`() {
        assertViolation(
            CreateUserRequest("a".repeat(256), "ana@taskflow.dev", "secreta123"),
            "name"
        )
        assertViolation(
            CreateUserRequest("Ana", "a".repeat(250) + "@x.com", "secreta123"),
            "email"
        )
    }

    @Test
    fun `actualizar usuario permite omitir campos pero rechaza email vacio`() {
        assertEquals(0, validator.validate(UpdateUserRequest()).size)
        assertViolation(UpdateUserRequest(email = ""), "email")
        assertViolation(UpdateUserRequest(email = "   "), "email")
    }

    @Test
    fun `crear tarea rechaza una descripcion formada solo por espacios`() {
        assertViolation(
            CreateTaskRequest("Informe", "     ", 1),
            "description"
        )
    }

    @Test
    fun `crear tarea rechaza titulos demasiado largos`() {
        assertViolation(
            CreateTaskRequest("t".repeat(256), "Descripcion valida", 1),
            "title"
        )
    }

    @Test
    fun `crear tarea exige un userId positivo`() {
        assertViolation(CreateTaskRequest("Informe", "Descripcion valida", 0), "userId")
        assertViolation(CreateTaskRequest("Informe", "Descripcion valida", -1), "userId")
    }

    private fun assertViolation(value: Any, property: String) {
        val violations = validator.validate(value)
        assertTrue(
            violations.any { it.propertyPath.toString() == property },
            "Se esperaba una violacion en '$property', pero fueron: " +
                violations.map { it.propertyPath.toString() }
        )
    }
}
