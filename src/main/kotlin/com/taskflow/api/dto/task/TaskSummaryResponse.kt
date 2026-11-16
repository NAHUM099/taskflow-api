package com.taskflow.api.dto.task

data class TaskSummaryResponse (
    val total : Long,
    val pending : Long,
    val inProgress : Long,
    val done : Long,
    val cancelled : Long
)
