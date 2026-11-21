package com.taskflow.api.dto.user

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateUserRequest (

    @field:NotBlank(message = "nombre requerido")
    @field:Size(max = 255, message = "el nombre no puede superar 255 caracteres")
    val name : String,
    @field:NotBlank(message = "correo requerido")
    @field:Email(message = "correo invalido")
    @field:Size(max = 255, message = "el correo no puede superar 255 caracteres")
    val email : String,
    @field:Size(min = 6, message = "la contraseña debe tener minimo 6 caracteres")
    val password: String
)

