package com.taskflow.api.dto.user

import jakarta.validation.constraints.Size

// Uso exclusivo de ADMIN: establece la contraseña de un usuario por su id,
// sin exigir la contraseña anterior (a diferencia de UpdatePasswordRequest).
data class UpdateUserPasswordRequest (

    @field:Size(min = 6, message = "la contraseña debe tener minimo 6 caracteres")
    val newPassword : String
)
