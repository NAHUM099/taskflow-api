package com.taskflow.api.controller

import com.taskflow.api.dto.task.CreateTaskRequest
import com.taskflow.api.dto.task.StatusRequest
import com.taskflow.api.dto.task.TaskFilter
import com.taskflow.api.dto.task.TaskResponse
import com.taskflow.api.dto.task.TaskSummaryResponse
import com.taskflow.api.enums.TaskStatus
import com.taskflow.api.service.TaskService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@RestController
class TaskController (

    private val taskService: TaskService
) {

    @PostMapping("/tasks")
    fun createTask(
        @Valid @RequestBody request: CreateTaskRequest
    ): TaskResponse {

        return taskService.createTask(request)
    }


    @PatchMapping("/tasks/{id}/status")
    fun statusTask(
        authentication: Authentication,

        // pathVarible toma parametro de la url
        @PathVariable id: Long,

        // Convierte el JSON recibido en un objeto StatusRequest y valida
        // que sus campos cumplan las restricciones definidas en la clase
        @Valid @RequestBody request: StatusRequest
    ): TaskResponse {

        return taskService.patchStatusTask(id, request, authentication)
    }

    @GetMapping("/tasks")
    fun getTasks(
        authentication: Authentication,

        // Paginación estándar de Spring (?page=0&size=10). Por defecto, 10 tareas.
        @PageableDefault(size = 10) pageable: Pageable,

        // Obtiene el estado desde los parámetros de la URL.
        // Al ser opcional (required = false), la petición puede realizarse sin enviar status.
        @RequestParam(required = false) status: TaskStatus?,
        @RequestParam(required = false) userId: Long?,

        // Búsqueda por nombre de tarea (coincidencia parcial, sin distinguir mayúsculas)
        @RequestParam(required = false) search: String?
    ): Page<TaskResponse> {

        val param = TaskFilter(status, userId, search)
        return taskService.getTasks(param, pageable, authentication)
    }

    @GetMapping("/tasks/summary")
    fun getTasksSummary(authentication: Authentication): TaskSummaryResponse {

        return taskService.getTasksSummary(authentication)
    }

}