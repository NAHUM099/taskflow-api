package com.taskflow.api.repository

import com.taskflow.api.enums.TaskStatus

/**
 * Proyección para el conteo de tareas agrupadas por estado.
 */
interface TaskStatusCount {

    fun getStatus() : TaskStatus

    fun getTotal() : Long
}
