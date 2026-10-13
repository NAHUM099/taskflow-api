package com.taskflow.api.dto.task

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size


data class CreateTaskRequest (
    @field:NotBlank(message = "el titulo es requerido")
    val title : String,
    @field:Size(min = 5, max = 200, message = "la descripcion debe tener un maximo de 200 caracteres")
    val description : String,
    val userId : Long
)