package com.taskflow.api.dto.user

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank


data class UpdateUserRequest (


    val name : String? = null,

    @field:Email(message = "email invalido")
    val email : String? = null
)