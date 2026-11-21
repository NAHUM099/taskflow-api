package com.taskflow.api.dto.task

import com.taskflow.api.enums.TaskStatus
import jakarta.validation.constraints.NotNull

data class StatusRequest (
    @field:NotNull(message = "el estado es requerido")
    val status : TaskStatus
)