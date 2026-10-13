package com.taskflow.api.dto.user

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateUserRequest (

    @field:NotBlank(message = "nombre requerido")
    val name : String,
    @field:Email(message = "correo invalido")
    val email : String,
    @field:Size(min = 6, message = "la contraseña debe tener minimo 6 caracteres")
    val password: String
)

