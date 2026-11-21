package com.taskflow.api.mapper

import com.taskflow.api.dto.task.CreateTaskRequest
import com.taskflow.api.dto.task.TaskResponse
import com.taskflow.api.entity.TaskEntity
import com.taskflow.api.entity.UserEntity
import com.taskflow.api.enums.TaskStatus

import java.time.LocalDateTime

object TaskMapper {


    fun toEntity (request : CreateTaskRequest, user : UserEntity) : TaskEntity {

            return TaskEntity (
                title = request.title,
                description = request.description,
                status = TaskStatus.PENDING,
                createdAt = LocalDateTime.now(),
                user = user
            )
    }

    fun toResponse(taskEntity: TaskEntity) : TaskResponse {

        // El propietario se obtiene una sola vez. Las consultas del repositorio
        // lo traen inicializado para que este acceso no dependa de OSIV.
        val owner = taskEntity.user

        return TaskResponse(
            id = taskEntity.id,
            title = taskEntity.title,
            description = taskEntity.description,
            status = taskEntity.status,
            userId = owner.id,
            userName = owner.name,
            createdAt = taskEntity.createdAt
        )
    }
}