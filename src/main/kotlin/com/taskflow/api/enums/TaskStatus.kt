package com.taskflow.api.enums

enum class TaskStatus(
    val allowedTransitions: Set<TaskStatus>
) {

    DONE(emptySet()),
    CANCELLED(emptySet()),
    IN_PROGRESS (setOf(DONE, CANCELLED)),
    PENDING(setOf(IN_PROGRESS, CANCELLED));


    fun canTransitionTo(newStatus: TaskStatus): Boolean {
        return newStatus in allowedTransitions
    }
}