package com.taskflow.api.repository


import com.taskflow.api.entity.TaskEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface TaskRepository : JpaRepository<TaskEntity, Long>, JpaSpecificationExecutor<TaskEntity> {

    // El listado necesita el nombre del propietario. El EntityGraph evita una
    // consulta adicional por cada tarea (N+1) sin abrir la sesión de Hibernate
    // fuera de la transacción del servicio.
    @EntityGraph(attributePaths = ["user"])
    fun findAllByUserId(id : Long) : List<TaskEntity>

    // findAll(Specification, Pageable) se redeclara para aplicar el mismo
    // EntityGraph al listado paginado y filtrado de /tasks.
    @EntityGraph(attributePaths = ["user"])
    override fun findAll(spec: Specification<TaskEntity>?, pageable: Pageable): Page<TaskEntity>

    // Para el cambio de estado se trae el propietario en la misma consulta.
    @Query("SELECT t FROM TaskEntity t JOIN FETCH t.user WHERE t.id = :id")
    fun findWithUserById(@Param("id") id : Long) : Optional<TaskEntity>

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