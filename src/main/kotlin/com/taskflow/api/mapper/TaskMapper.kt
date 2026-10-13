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

        return TaskResponse(
            id = taskEntity.id,
            title = taskEntity.title,
            description = taskEntity.description,
            status = taskEntity.status,
            userId = taskEntity.user.id,
            userName = taskEntity.user.name,
            createdAt = taskEntity.createdAt
        )
    }
}