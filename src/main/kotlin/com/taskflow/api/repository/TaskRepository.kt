package com.taskflow.api.repository


import com.taskflow.api.entity.TaskEntity
import com.taskflow.api.enums.TaskStatus
import org.springframework.data.jpa.repository.JpaRepository

interface TaskRepository : JpaRepository<TaskEntity, Long> {

    fun findAllByUserId(id : Long) : List<TaskEntity>

    fun findByStatus(status : TaskStatus) : List<TaskEntity>

    fun findByStatusAndUserId(status : TaskStatus, id : Long) : List<TaskEntity>

    fun findAllByOrderByCreatedAtDesc() : List<TaskEntity>

    fun findByStatusOrderByCreatedAtDesc(status : TaskStatus) : List<TaskEntity>

    fun findAllByUserIdOrderByCreatedAtDesc(id : Long) : List<TaskEntity>

    fun findByStatusAndUserIdOrderByCreatedAtDesc(status : TaskStatus, id : Long) : List<TaskEntity>
}