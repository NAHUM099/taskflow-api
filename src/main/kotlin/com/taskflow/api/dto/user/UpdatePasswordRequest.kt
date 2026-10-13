package com.taskflow.api.dto.user

import jakarta.validation.constraints.Size

data class UpdatePasswordRequest (

    val currentPassword : String,

    @field:Size(min = 6, message = "la contraseña debe tener minimo 6 caracteres")
    val newPassword: String
)

