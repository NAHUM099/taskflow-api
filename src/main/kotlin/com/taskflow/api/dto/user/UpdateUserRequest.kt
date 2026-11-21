package com.taskflow.api.dto.user

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size


data class UpdateUserRequest (

    @field:Size(max = 255, message = "el nombre no puede superar 255 caracteres")
    @field:Pattern(regexp = "(?s).*\\S.*", message = "el nombre no puede estar vacio")
    val name : String? = null,

    // Los campos de un PATCH pueden omitirse (null), pero si se envían no
    // pueden ser cadenas vacías o formadas solo por espacios.
    @field:Email(message = "email invalido")
    @field:Pattern(regexp = "(?s).*\\S.*", message = "el correo no puede estar vacio")
    @field:Size(max = 255, message = "el correo no puede superar 255 caracteres")
    val email : String? = null
)