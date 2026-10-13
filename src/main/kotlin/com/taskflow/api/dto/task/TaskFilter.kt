package com.taskflow.api.dto.task

import com.taskflow.api.enums.TaskStatus

data class TaskFilter (
    val status : TaskStatus?,
    val userId : Long?
)