package com.taskflow.api.dto.task

import com.taskflow.api.enums.TaskStatus
import jakarta.validation.constraints.NotBlank
import org.jetbrains.annotations.NotNull

data class StatusRequest (
    @field:NotNull
    val status : TaskStatus
)