package com.taskflow.api.dto.task

import com.taskflow.api.enums.TaskStatus
import java.time.LocalDateTime

data class TaskResponse (
    val id : Long?,
    val title : String,
    val description : String,
    val status : TaskStatus,
    val userId : Long?,
    val userName: String,
    val createdAt : LocalDateTime
)