package com.taskflow.api.dto.task

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size


data class CreateTaskRequest (
    @field:NotBlank(message = "el titulo es requerido")
    @field:Size(max = 255, message = "el titulo no puede superar 255 caracteres")
    val title : String,
    @field:NotBlank(message = "la descripcion es requerida")
    @field:Size(min = 5, max = 200, message = "la descripcion debe tener un maximo de 200 caracteres")
    val description : String,
    @field:Positive(message = "el userId debe ser positivo")
    val userId : Long
)