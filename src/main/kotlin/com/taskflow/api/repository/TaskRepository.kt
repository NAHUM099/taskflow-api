package com.taskflow.api.repository


import com.taskflow.api.entity.TaskEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TaskRepository : JpaRepository<TaskEntity, Long>, JpaSpecificationExecutor<TaskEntity> {

    fun findAllByUserId(id : Long) : List<TaskEntity>

    // Conteo por estado en una sola consulta, usado por el resumen del dashboard
    @Query("SELECT t.status AS status, COUNT(t) AS total FROM TaskEntity t GROUP BY t.status")
    fun countGroupedByStatus() : List<TaskStatusCount>

    // Mismo conteo, pero restringido a las tareas asignadas a un usuario (rol USER)
    @Query(
        "SELECT t.status AS status, COUNT(t) AS total " +
            "FROM TaskEntity t WHERE t.user.id = :userId GROUP BY t.status"
    )
    fun countGroupedByStatusByUserId(@Param("userId") userId : Long) : List<TaskStatusCount>
}